package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.VertexBuffer
import org.joml.Vector3f
import java.io.File
import java.util.ArrayList
import java.util.EnumSet

abstract class AbstractDeferredRenderer(protected val programManager: ProgramManager<OpenGl>,
                                    val config: Config,
                                    override val deferredRenderingBuffer: DeferredRenderingBuffer) : Renderer<OpenGl> {
    protected val gpuContext: GpuContext<OpenGl> = programManager.gpuContext
    override val renderExtensions: List<RenderExtension<OpenGl>> = mutableListOf()

    protected open var finalImage = deferredRenderingBuffer.finalBuffer.renderedTexture
    protected val renderToQuadProgram: Program = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))

    override fun render(result: DrawResult, state: RenderState) {
        drawToQuad(finalImage, gpuContext.fullscreenBuffer)
    }

    override fun drawToQuad(texture: Int) {
        drawToQuad(texture, gpuContext.fullscreenBuffer, renderToQuadProgram)
    }

    fun drawToQuad(texture: Int, buffer: VertexBuffer) {
        drawToQuad(texture, buffer, renderToQuadProgram)
    }

    private fun drawToQuad(texture: Int, buffer: VertexBuffer, program: Program) {
        program.use()

        gpuContext.bindFrameBuffer(0)
        gpuContext.viewPort(0, 0, gpuContext.window.width, gpuContext.window.height)
        gpuContext.disable(GlCap.DEPTH_TEST)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}