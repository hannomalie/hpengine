package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.math.identityMatrix4fBuffer
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.vertexbuffer.drawLines
import org.joml.Vector3f
import org.joml.Vector3fc
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.function.Consumer
import kotlin.math.min

fun EngineContext.drawLines(
        vertices: PersistentMappedStructBuffer<HpVector4f>,
        linePoints: List<Vector3fc>,
        lineWidth: Float = 5f,
        modelMatrix: FloatBuffer = identityMatrix4fBuffer,
        viewMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.viewMatrixAsBuffer,
        projectionMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.projectionMatrixAsBuffer,
        color: Vector3fc) {

    if (linePoints.isEmpty()) return
    vertices.ensureCapacityInBytes(linePoints.size * vertices.slidingWindow.sizeInBytes)

    for (i in linePoints.indices) {
        vertices[i].set(linePoints[i])
    }

    drawLines(vertices, lineWidth, linePoints.size, modelMatrix, viewMatrix, projectionMatrix, color)
}
fun EngineContext.drawLines(
        vertices: PersistentMappedStructBuffer<HpVector4f>,
        lineWidth: Float = 5f,
        verticesCount: Int,
        modelMatrix: FloatBuffer = identityMatrix4fBuffer,
        viewMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.viewMatrixAsBuffer,
        projectionMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.projectionMatrixAsBuffer,
        color: Vector3fc) {

    if (verticesCount <= 0) return

    val linesProgram = programManager.linesProgram
    linesProgram.use()
    linesProgram.setUniformAsMatrix4("modelMatrix", modelMatrix)
    linesProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
    linesProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
    linesProgram.setUniform("color", color)
    linesProgram.bindShaderStorageBuffer(7, vertices)

    drawLines(min(lineWidth, gpuContext.maxLineWidth), verticesCount)
}
