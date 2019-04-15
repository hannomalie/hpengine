package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import java.io.File
import java.util.function.Consumer

open class AbstractRenderer(programManager: ProgramManager<OpenGl>) : Renderer<OpenGl> {
    protected val programManager: ProgramManager<OpenGl> = programManager
    protected val gpuContext: GpuContext<OpenGl> = programManager.gpuContext

    protected val deferredRenderingBuffer = DeferredRenderingBuffer(gpuContext)
    protected open var finalImage = deferredRenderingBuffer.finalBuffer.renderedTexture
    protected val renderToQuadProgram: Program = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))

    override fun render(result: DrawResult, state: RenderState) {
        drawToQuad(finalImage, gpuContext.fullscreenBuffer)
    }

    override fun drawToQuad(texture: Int) {
        drawToQuad(texture, gpuContext.fullscreenBuffer, renderToQuadProgram)
    }

    override fun getRenderExtensions(): List<RenderExtension<OpenGl>> {
        return emptyList()
    }

    fun drawToQuad(texture: Int, buffer: VertexBuffer) {
        drawToQuad(texture, buffer, renderToQuadProgram!!)
    }

    private fun drawToQuad(texture: Int, buffer: VertexBuffer, program: Program) {
        program.use()

        gpuContext.bindFrameBuffer(0)
        gpuContext.viewPort(0, 0, gpuContext.canvasWidth, gpuContext.canvasHeight)
        gpuContext.disable(GlCap.DEPTH_TEST)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }

    override fun drawLines(program: Program): Int {
        return 0
    }

    override fun batchLine(from: Vector3f, to: Vector3f) {}
    override fun endFrame() {
        GPUProfiler.endFrame()
    }

    override fun drawAllLines(action: Consumer<Program>) { }
    override fun startFrame() {
        GPUProfiler.startFrame()
    }

    override fun getGBuffer() = deferredRenderingBuffer
}