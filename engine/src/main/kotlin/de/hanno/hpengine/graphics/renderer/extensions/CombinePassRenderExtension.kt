package de.hanno.hpengine.graphics.renderer.extensions

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource

class CombinePassRenderExtension(private val config: Config,
                                 private val programManager: ProgramManager<OpenGl>,
                                 private val textureManager: TextureManager,
                                 private val gpuContext: GpuContext<OpenGl>,
                                 private val deferredRenderingBuffer: DeferredRenderingBuffer
): DeferredRenderExtension<OpenGl> {

    private val combineProgram = programManager.getProgram(
        config.EngineAsset("shaders/combine_pass_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/combine_pass_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
 Defines()
    )

    fun renderCombinePass(state: RenderState, renderTarget: RenderTarget<Texture2D> = deferredRenderingBuffer.finalBuffer) {
        if(!config.effects.isAutoExposureEnabled) {
            deferredRenderingBuffer.exposureBuffer.putValues(0, state.camera.exposure)
        }
        profiled("Combine pass") {
            renderTarget.use(gpuContext, false)

            textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, renderTarget.getRenderedTexture(0))

            combineProgram.use()
            combineProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
            combineProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
            combineProgram.setUniform("screenWidth", config.width.toFloat())
            combineProgram.setUniform("screenHeight", config.height.toFloat())
            combineProgram.setUniform("camPosition", state.camera.getPosition())
            combineProgram.setUniform("ambientColor", config.effects.ambientLight)
            combineProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
            combineProgram.setUniform("worldExposure", state.camera.exposure)
            combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", config.effects.isAutoExposureEnabled)
            combineProgram.setUniform("fullScreenMipmapCount", deferredRenderingBuffer.fullScreenMipmapCount)
            combineProgram.setUniform("activeProbeCount", state.environmentProbesState.activeProbeCount)
            combineProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)

            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.BLEND)
            gpuContext.depthMask = false
            gpuContext.disable(GlCap.CULL_FACE)

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationBuffer.getRenderedTexture(1))
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(0))
            gpuContext.bindTexture(7, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(1))
            //			getGpuContext().bindTexture(7, TEXTURE_CUBE_MAP_ARRAY, renderState.getEnvironmentProbesState().getEnvironmapsArray0Id());
            gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.reflectionMap)
            gpuContext.bindTexture(9, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.refractedMap)
            gpuContext.bindTexture(11, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.ambientOcclusionScatteringMap)
            gpuContext.bindTexture(14, GlTextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
            gpuContext.bindTexture(15, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.halfScreenBuffer.renderedTextures[1])

            gpuContext.fullscreenBuffer.draw()

        }
    }
}