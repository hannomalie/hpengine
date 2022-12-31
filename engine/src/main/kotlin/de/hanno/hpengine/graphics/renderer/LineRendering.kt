package de.hanno.hpengine.graphics.renderer


import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.pipelines.TypedGpuBuffer
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.vertexbuffer.drawLines
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.math.identityMatrix4fBuffer
import org.joml.Vector3fc
import struktgen.api.forIndex
import java.nio.FloatBuffer
import kotlin.math.min

context(GraphicsApi)
fun drawLines(
    programManager: ProgramManager,
    linesProgram: IProgram<LinesProgramUniforms>,
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
    ensureCapacityInBytes(linePoints.size * Vector4fStrukt.type.sizeInBytes)

    for (i in linePoints.indices) {
        forIndex(i) { it.set(linePoints[i]) }
    }
}

context(GraphicsApi)
fun drawLines(
    linesProgram: IProgram<LinesProgramUniforms>,
    vertices: TypedGpuBuffer<Vector4fStrukt>,
    lineWidth: Float = 5f,
    verticesCount: Int,
    modelMatrix: FloatBuffer = identityMatrix4fBuffer,
    viewMatrix: FloatBuffer,
    projectionMatrix: FloatBuffer,
    color: Vector3fc
) {

    if (verticesCount <= 0) return

    linesProgram.useAndBind { uniforms ->
        uniforms.modelMatrix.safePut(modelMatrix)
        uniforms.projectionMatrix.safePut(projectionMatrix)
        uniforms.viewMatrix.safePut(viewMatrix)
        uniforms.color.set(color)
        uniforms.vertices = vertices
    }
    drawLines(min(lineWidth, maxLineWidth), verticesCount)
}

