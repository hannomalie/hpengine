package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import java.io.File

class CombinePassRenderExtension(val engineContext: EngineContext): RenderExtension<OpenGl> {

    private val combineProgram = engineContext.programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "combine_pass_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "combine_pass_fragment.glsl")))
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer
    private val gpuContext = engineContext.gpuContext

    fun renderCombinePass(state: RenderState, renderTarget: RenderTarget<Texture2D> = deferredRenderingBuffer.finalBuffer) {
        if(!engineContext.config.effects.isAutoExposureEnabled) {
            deferredRenderingBuffer.exposureBuffer.putValues(0, state.camera.exposure)
        }
        profiled("Combine pass") {
            renderTarget.use(gpuContext, false)

            engineContext.textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, renderTarget.getRenderedTexture(0))

            combineProgram.use()
            combineProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
            combineProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
            combineProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            combineProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            combineProgram.setUniform("camPosition", state.camera.getPosition())
            combineProgram.setUniform("ambientColor", engineContext.config.effects.ambientLight)
            combineProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
            combineProgram.setUniform("worldExposure", state.camera.exposure)
            combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", engineContext.config.effects.isAutoExposureEnabled)
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
            gpuContext.bindTexture(14, GlTextureTarget.TEXTURE_CUBE_MAP, engineContext.textureManager.cubeMap!!.id)
            gpuContext.bindTexture(15, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.halfScreenBuffer.renderedTextures[1])

            gpuContext.fullscreenBuffer.draw()

        }
    }
}