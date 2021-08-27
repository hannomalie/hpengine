package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

class PostProcessingExtension(private val config: Config,
                              private val programManager: ProgramManager<OpenGl>,
                              private val textureManager: TextureManager,
                              private val gpuContext: GpuContext<OpenGl>,
                              private val deferredRenderingBuffer: DeferredRenderingBuffer
): DeferredRenderExtension<OpenGl> {

    private val postProcessProgram = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/" + "passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/" + "postprocess_fragment.glsl")))

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        profiled("Post processing") {
            postProcessProgram.use()
            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.finalBuffer.getRenderedTexture(0))
            postProcessProgram.setUniform("screenWidth", config.width.toFloat())
            postProcessProgram.setUniform("screenHeight", config.height.toFloat())
            postProcessProgram.setUniform("worldExposure", renderState.camera.exposure)
            postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", config.effects.isAutoExposureEnabled)
            postProcessProgram.setUniform("usePostProcessing", config.effects.isEnablePostprocessing)
            postProcessProgram.setUniform("cameraRightDirection", renderState.camera.getRightDirection())
            postProcessProgram.setUniform("cameraViewDirection", renderState.camera.getViewDirection())
            postProcessProgram.setUniform("focalDepth", renderState.camera.focalDepth)
            postProcessProgram.setUniform("focalLength", renderState.camera.focalLength)
            postProcessProgram.setUniform("fstop", renderState.camera.fStop)
            postProcessProgram.setUniform("znear", renderState.camera.near)
            postProcessProgram.setUniform("zfar", renderState.camera.far)

            postProcessProgram.setUniform("seconds", renderState.deltaSeconds)
            postProcessProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
            //        postProcessProgram.bindShaderStorageBuffer(1, managerContext.getRenderer().getMaterialManager().getMaterialBuffer());
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, textureManager.lensFlareTexture.id)
            gpuContext.fullscreenBuffer.draw()
        }
    }
}