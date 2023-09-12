package de.hanno.hpengine.graphics.renderer.extensions


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.draw
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource

class PostProcessingExtension(
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: TextureManager,
    private val graphicsApi: GraphicsApi,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {

    private val fullscreenBuffer = graphicsApi.run { QuadVertexBuffer(graphicsApi) }
    private val postProcessProgram = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/" + "passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/" + "postprocess_fragment.glsl"))
    )

    override fun renderSecondPassFullScreen(renderState: RenderState): Unit = graphicsApi.run {
        profiled("Post processing") {
            val camera = renderState[primaryCameraStateHolder.camera]
            graphicsApi.run { postProcessProgram.use() }
            graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.finalBuffer.getRenderedTexture(0))
            postProcessProgram.setUniform("screenWidth", config.width.toFloat())
            postProcessProgram.setUniform("screenHeight", config.height.toFloat())
            postProcessProgram.setUniform("worldExposure", camera.exposure)
            postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", config.effects.isAutoExposureEnabled)
            postProcessProgram.setUniform("usePostProcessing", config.effects.isEnablePostprocessing)
            postProcessProgram.setUniform("cameraRightDirection", camera.getRightDirection())
            postProcessProgram.setUniform("cameraViewDirection", camera.getViewDirection())
            postProcessProgram.setUniform("focalDepth", camera.focalDepth)
            postProcessProgram.setUniform("focalLength", camera.focalLength)
            postProcessProgram.setUniform("fstop", camera.fStop)
            postProcessProgram.setUniform("znear", camera.near)
            postProcessProgram.setUniform("zfar", camera.far)

            postProcessProgram.setUniform("seconds", renderState.deltaSeconds)
            postProcessProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
            //        postProcessProgram.bindShaderStorageBuffer(1, managerContext.getRenderer().getMaterialManager().getMaterialBuffer());
            bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            bindTexture(4, TextureTarget.TEXTURE_2D, textureManager.lensFlareTexture.id)
            fullscreenBuffer.draw()
        }
    }
}