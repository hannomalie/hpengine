package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource

class AOScatteringExtension(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager
): RenderExtension<OpenGl> {
    val gBuffer = deferredRenderingBuffer
    private val aoScatteringProgram = programManager.getProgram(
            config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
            config.engineDir.resolve("shaders/scattering_ao_fragment.glsl").toCodeSource(), Uniforms.Empty
    )

    override fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        profiled("Scattering and AO") {
            if (!config.quality.isUseAmbientOcclusion && !config.effects.isScattering) {
                return
            }

            gpuContext.disable(GlCap.DEPTH_TEST)
            aoScatteringProgram.use()

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, gBuffer.positionMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, gBuffer.motionMap)
            gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
            renderState.lightState.pointLightShadowMapStrategy.bindTextures()
            if(renderState.environmentProbesState.environmapsArray3Id > 0) {
                gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
            }

            aoScatteringProgram.setUniform("eyePosition", renderState.camera.getPosition())
            aoScatteringProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
            aoScatteringProgram.setUniform("ambientOcclusionRadius", config.effects.ambientocclusionRadius)
            aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", config.effects.ambientocclusionTotalStrength)
            aoScatteringProgram.setUniform("screenWidth", config.width.toFloat() / 2f)
            aoScatteringProgram.setUniform("screenHeight", config.height.toFloat() / 2f)
            aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
            aoScatteringProgram.setUniform("time", renderState.time.toInt())
            //		aoScatteringProgram.setUniform("useVoxelGrid", directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null);
            //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
            //			aoScatteringProgram.bindShaderStorageBuffer(5, renderState.getState(directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGridBufferRef()).getVoxelGridBuffer());
            //		}

            aoScatteringProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            aoScatteringProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
            aoScatteringProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            aoScatteringProgram.bindShaderStorageBuffer(3, renderState.directionalLightState)

            gpuContext.fullscreenBuffer.draw()
            profiled("generate mipmaps") {
                gpuContext.enable(GlCap.DEPTH_TEST)
                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, gBuffer.halfScreenBuffer.renderedTexture)
                textureManager.blur2DTextureRGBA16F(gBuffer.halfScreenBuffer.renderedTexture, config.width / 2, config.height / 2, 0, 0)
            }
        }
    }
}
