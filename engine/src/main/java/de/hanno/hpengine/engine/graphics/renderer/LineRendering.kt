package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.shader.safePut
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.math.identityMatrix4fBuffer
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.vertexbuffer.drawLines
import org.joml.Vector3fc
import java.nio.FloatBuffer
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

    vertices.putLinesPoints(linePoints)

    drawLines(vertices, lineWidth, linePoints.size, modelMatrix, viewMatrix, projectionMatrix, color)
}

fun PersistentMappedStructBuffer<HpVector4f>.putLinesPoints(linePoints: List<Vector3fc>) {
    ensureCapacityInBytes(linePoints.size * slidingWindow.sizeInBytes)

    for (i in linePoints.indices) {
        this[i].set(linePoints[i])
    }
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

    programManager.linesProgram.useAndBind { uniforms ->
        uniforms.modelMatrix.safePut(modelMatrix)
        uniforms.projectionMatrix.safePut(projectionMatrix)
        uniforms.viewMatrix.safePut(viewMatrix)
        uniforms.color.set(color)
        uniforms.vertices = vertices
    }
    drawLines(min(lineWidth, programManager.gpuContext.maxLineWidth), verticesCount)
}

