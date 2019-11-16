package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.draw
import java.io.File

class PostProcessingExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val gpuContext = engineContext.gpuContext
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer
    private val postProcessProgram = engineContext.programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "postprocess_fragment.glsl")))

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        engineContext.window.frontBuffer.use(gpuContext, true)
        profiled("Post processing") {
            postProcessProgram.use()
            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.finalBuffer.getRenderedTexture(0))
            postProcessProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            postProcessProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            postProcessProgram.setUniform("worldExposure", renderState.camera.exposure)
            postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", engineContext.config.effects.isAutoExposureEnabled)
            postProcessProgram.setUniform("usePostProcessing", engineContext.config.effects.isEnablePostprocessing)
            postProcessProgram.setUniform("cameraRightDirection", renderState.camera.getRightDirection())
            postProcessProgram.setUniform("cameraViewDirection", renderState.camera.getViewDirection())
            postProcessProgram.setUniform("focalDepth", renderState.camera.focalDepth)
            postProcessProgram.setUniform("focalLength", renderState.camera.focalLength)
            postProcessProgram.setUniform("fstop", renderState.camera.fStop)
            postProcessProgram.setUniform("znear", renderState.camera.near)
            postProcessProgram.setUniform("zfar", renderState.camera.far)

            postProcessProgram.setUniform("seconds", renderState.deltaInS)
            postProcessProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
            //        postProcessProgram.bindShaderStorageBuffer(1, managerContext.getRenderer().getMaterialManager().getMaterialBuffer());
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, engineContext.textureManager.lensFlareTexture.id)
            gpuContext.fullscreenBuffer.draw()
        }
    }
}