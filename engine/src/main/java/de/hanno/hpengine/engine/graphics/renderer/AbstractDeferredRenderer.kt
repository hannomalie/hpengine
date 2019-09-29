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
import org.lwjgl.opengl.GL11
import java.io.File
import java.util.ArrayList
import java.util.EnumSet
import java.util.function.Consumer

open class AbstractDeferredRenderer(protected val programManager: ProgramManager<OpenGl>,
                                    val config: Config,
                                    val deferredRenderingBuffer: DeferredRenderingBuffer) : Renderer<OpenGl> {
    protected val gpuContext: GpuContext<OpenGl> = programManager.gpuContext

    protected open var finalImage = deferredRenderingBuffer.finalBuffer.renderedTexture
    protected val renderToQuadProgram: Program = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))

    private val linePoints = ArrayList<Vector3f>()

    private val buffer = VertexBuffer(gpuContext, floatArrayOf(0f, 0f, 0f, 0f), EnumSet.of(DataChannels.POSITION3)).apply {
        upload()
    }

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

    override fun drawLines(program: Program): Int {
        val points = FloatArray(linePoints.size * 3)
        for (i in linePoints.indices) {
            val point = linePoints[i]
            points[3 * i] = point.x
            points[3 * i + 1] = point.y
            points[3 * i + 2] = point.z
        }
        buffer.putValues(*points)
        buffer.upload().join()
        buffer.drawDebugLines()
        GL11.glFinish()
        linePoints.clear()
        return points.size / 3 / 2
    }

    override fun batchLine(from: Vector3f, to: Vector3f) {
        linePoints.add(from)
        linePoints.add(to)
    }

    override fun drawAllLines(action: Consumer<Program>) { }

    override fun getGBuffer() = deferredRenderingBuffer
}