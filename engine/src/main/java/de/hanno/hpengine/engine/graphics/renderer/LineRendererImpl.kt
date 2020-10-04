package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import de.hanno.hpengine.engine.vertexbuffer.drawLines
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import java.util.ArrayList
import java.util.function.Consumer
import kotlin.math.min

class LineRendererImpl(val engineContext: EngineContext) : LineRenderer {

    override val linePoints = ArrayList<Vector3fc>()
    private val vertices = PersistentMappedStructBuffer(100, engineContext.gpuContext, { VertexStructPacked() })
    private val linesProgram = engineContext.run { programManager.getProgram(
            EngineAsset("shaders/mvp_vertex.glsl"),
            EngineAsset("shaders/simple_color_fragment.glsl"))
    }

//    TODO: This has to be implemented in context
    private val maxLineWidth = engineContext.backend.gpuContext.window.invoke { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }

    override fun batchLine(from: Vector3fc, to: Vector3fc) {
        linePoints.add(from)
        linePoints.add(to)
    }

    override fun batchPointForLine(point: Vector3f) {
        linePoints.add(point)
    }

    override fun drawAllLines(lineWidth: Float, action: Consumer<Program>) {
        if(linePoints.isEmpty()) return
        linesProgram.use()
        action.accept(linesProgram)
        drawLines(linePoints, linesProgram, lineWidth)
        linePoints.clear()
    }

    fun drawLines(linePoints: List<Vector3fc>, lineWidth: Float, action: Consumer<Program>) {
        if(linePoints.isEmpty()) return
        linesProgram.use()
        action.accept(linesProgram)
        drawLines(linePoints, linesProgram, lineWidth)
    }

    override fun drawLines(linePoints: List<Vector3fc>, program: Program, lineWidth: Float): Int {
        vertices.ensureCapacityInBytes(linePoints.size * vertices.slidingWindow.sizeInBytes)

        for (i in linePoints.indices) {
            val point = linePoints[i]
            vertices[i].position.apply {
                x = point.x
                y = point.y
                z = point.z
                w = 1.0f
            }
        }
        engineContext.gpuContext.window { GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0) }
        program.bindShaderStorageBuffer(7, vertices)

        drawLines(min(lineWidth, maxLineWidth), linePoints.size)

        GL11.glFinish()
        return linePoints.size
    }
}