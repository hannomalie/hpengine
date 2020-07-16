package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.vertexbuffer.DataChannels
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.drawDebugLines
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.util.ArrayList
import java.util.EnumSet
import java.util.function.Consumer
import kotlin.math.min

class LineRendererImpl(engineContext: EngineContext<OpenGl>) : LineRenderer {

    private val programManager: ProgramManager<OpenGl> = engineContext.programManager
    private val linePoints = ArrayList<Vector3f>()
    private val linesProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl")

//    TODO: This has to be implemented in context
    private val maxLineWidth = engineContext.backend.gpuContext.window.invoke { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }
    private val buffer = VertexBuffer(engineContext.gpuContext, EnumSet.of(DataChannels.POSITION3), floatArrayOf(0f, 0f, 0f, 0f)).apply {
        upload()
    }

    override fun batchLine(from: Vector3f, to: Vector3f) {
        linePoints.add(from)
        linePoints.add(to)
    }

    override fun batchPointForLine(point: Vector3f) {
        linePoints.add(point)
    }

    override fun drawAllLines(lineWidth: Float, action: Consumer<Program>) {
        linesProgram.use()
        action.accept(linesProgram)
        drawLines(linesProgram, lineWidth)
        linePoints.clear()
    }

    override fun drawLines(program: Program, lineWidth: Float): Int {
        val points = FloatArray(linePoints.size * 3)
        for (i in linePoints.indices) {
            val point = linePoints[i]
            points[3 * i] = point.x
            points[3 * i + 1] = point.y
            points[3 * i + 2] = point.z
        }
        buffer.putValues(*points)
        buffer.upload().join()
        buffer.drawDebugLines(min(lineWidth, maxLineWidth))
        GL11.glFinish()
        linePoints.clear()
        return points.size / 3 / 2
    }
}