package de.hanno.hpengine.graphics.renderer


import Vector4fStruktImpl
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.safePut
import de.hanno.hpengine.toCount
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.buffer.vertex.drawLines
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.math.identityMatrix4fBuffer
import org.joml.Vector3fc
import struktgen.api.forIndex
import java.nio.FloatBuffer
import kotlin.math.min

fun GraphicsApi.drawLines(
    linesProgram: Program<LinesProgramUniforms>,
    vertices: TypedGpuBuffer<Vector4fStrukt>,
    linePoints: List<Vector3fc>,
    lineWidth: Float = 5f,
    modelMatrix: FloatBuffer = identityMatrix4fBuffer,
    viewMatrix: FloatBuffer,
    projectionMatrix: FloatBuffer,
    color: Vector3fc
) {

    if (linePoints.isEmpty()) return

    vertices.putLinesPoints(linePoints)

    drawLines(
        linesProgram,
        vertices,
        lineWidth,
        linePoints.size,
        modelMatrix,
        viewMatrix,
        projectionMatrix,
        color
    )
}

fun TypedGpuBuffer<Vector4fStrukt>.putLinesPoints(linePoints: List<Vector3fc>) {
    ensureCapacityInBytes(linePoints.size.toCount() * SizeInBytes(Vector4fStruktImpl.sizeInBytes))

    for (i in linePoints.indices) {
        forIndex(i) { it.set(linePoints[i]) }
    }
}

fun GraphicsApi.drawLines(
    linesProgram: Program<LinesProgramUniforms>,
    vertices: TypedGpuBuffer<Vector4fStrukt>,
    lineWidth: Float = 5f,
    verticesCount: Int,
    modelMatrix: FloatBuffer = identityMatrix4fBuffer,
    viewMatrix: FloatBuffer,
    projectionMatrix: FloatBuffer,
    color: Vector3fc
) {

    if (verticesCount <= 0) return

    linesProgram.useAndBind({
        this.modelMatrix.safePut(modelMatrix)
        this.projectionMatrix.safePut(projectionMatrix)
        this.viewMatrix.safePut(viewMatrix)
        this.color.set(color)
        this.vertices = vertices
    }, {
        drawLines(min(lineWidth, maxLineWidth), verticesCount)
    })
}

