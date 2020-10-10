package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource

class AOScatteringExtension(val engineContext: EngineContext): RenderExtension<OpenGl> {
    val gBuffer = engineContext.deferredRenderingBuffer
    val backend = engineContext.gpuContext.backend
    val gpuContext = engineContext.gpuContext
    private val aoScatteringProgram = engineContext.programManager.getProgram(
            engineContext.config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
            engineContext.config.engineDir.resolve("shaders/scattering_ao_fragment.glsl").toCodeSource(), Uniforms.Empty
    )

    override fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        profiled("Scattering and AO") {
            if (!engineContext.config.quality.isUseAmbientOcclusion && !engineContext.config.effects.isScattering) {
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
            aoScatteringProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
            aoScatteringProgram.setUniform("ambientOcclusionRadius", engineContext.config.effects.ambientocclusionRadius)
            aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", engineContext.config.effects.ambientocclusionTotalStrength)
            aoScatteringProgram.setUniform("screenWidth", engineContext.config.width.toFloat() / 2f)
            aoScatteringProgram.setUniform("screenHeight", engineContext.config.height.toFloat() / 2f)
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

            EnvironmentProbeManager.bindEnvironmentProbePositions(aoScatteringProgram, renderState.environmentProbesState)
            gpuContext.fullscreenBuffer.draw()
            profiled("generate mipmaps") {
                gpuContext.enable(GlCap.DEPTH_TEST)
                engineContext.textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, gBuffer.halfScreenBuffer.renderedTexture)
                engineContext.textureManager.blur2DTextureRGBA16F(gBuffer.halfScreenBuffer.renderedTexture, engineContext.config.width / 2, engineContext.config.height / 2, 0, 0)
            }
        }
    }
}
