package de.hanno.hpengine.graphics.renderer.deferred.extensions


import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.constants.Capability
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbesStateHolder
import de.hanno.hpengine.graphics.light.point.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.koin.core.annotation.Single
import struktgen.api.forIndex

@Single(binds = [AOScatteringExtension::class, DeferredRenderExtension::class])
class AOScatteringExtension(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val pointLightSystem: PointLightSystem,
): DeferredRenderExtension {
    private val fullscreenBuffer = graphicsApi.run { QuadVertexBuffer(graphicsApi) }
    val gBuffer = deferredRenderingBuffer
    private val aoScatteringProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/scattering_ao_fragment.glsl").toCodeSource(), Uniforms.Empty, Defines()
    )

    override fun renderSecondPassHalfScreen(renderState: RenderState) = graphicsApi.run {
        profiled("Scattering and AO") {
            if (!config.quality.isUseAmbientOcclusion && !config.effects.isScattering) {
                return
            }
            val directionalLightState = renderState[directionalLightStateHolder.lightState]

            disable(Capability.DEPTH_TEST)
            aoScatteringProgram.use()

            bindTexture(0, TextureTarget.TEXTURE_2D, gBuffer.positionMap)
            bindTexture(1, TextureTarget.TEXTURE_2D, gBuffer.normalMap)
            bindTexture(2, TextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
            bindTexture(3, TextureTarget.TEXTURE_2D, gBuffer.motionMap)
            val directionalShadowMap = directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
            if(directionalShadowMap > -1) {
                bindTexture(6, TextureTarget.TEXTURE_2D, directionalShadowMap)
            }
            pointLightSystem.shadowMapStrategy.bindTextures()
            val environmentProbesState = renderState[environmentProbesStateHolder.state]
            if(environmentProbesState.environmapsArray3Id > 0) {
                graphicsApi.bindTexture(8, TextureTarget.TEXTURE_CUBE_MAP_ARRAY, environmentProbesState.environmapsArray3Id)
            }

            val camera = renderState[primaryCameraStateHolder.camera]

            aoScatteringProgram.setUniform("eyePosition", camera.getPosition())
            aoScatteringProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
            aoScatteringProgram.setUniform("ambientOcclusionRadius", config.effects.ambientocclusionRadius)
            aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", config.effects.ambientocclusionTotalStrength)
            aoScatteringProgram.setUniform("screenWidth", config.width.toFloat() / 2f)
            aoScatteringProgram.setUniform("screenHeight", config.height.toFloat() / 2f)
            aoScatteringProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
            aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixBuffer)
            aoScatteringProgram.setUniform("time", renderState.time.toInt())
            //		aoScatteringProgram.setUniform("useVoxelGrid", directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null);
            //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
            //			aoScatteringProgram.bindShaderStorageBuffer(5, renderState.getState(directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGridBufferRef()).getVoxelGridBuffer());
            //		}

            aoScatteringProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS)
            aoScatteringProgram.setUniform("pointLightCount", renderState[pointLightStateHolder.lightState].pointLightCount)
            aoScatteringProgram.bindShaderStorageBuffer(2,
                renderState[pointLightStateHolder.lightState].pointLightBuffer)
            aoScatteringProgram.bindShaderStorageBuffer(3, directionalLightState)

            fullscreenBuffer.draw(indexBuffer = null)
            profiled("generate mipmaps") {
                graphicsApi.enable(Capability.DEPTH_TEST)
                graphicsApi.generateMipMaps(gBuffer.halfScreenBuffer.textures[0])
                textureManager.blur2DTextureRGBA16F(gBuffer.halfScreenBuffer.renderedTexture, config.width / 2, config.height / 2, 0, 0)
            }
        }
    }
}
