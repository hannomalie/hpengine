package de.hanno.hpengine.graphics.renderer


import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentTypedBuffer
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.math.identityMatrix4fBuffer
import de.hanno.hpengine.graphics.vertexbuffer.drawLines
import de.hanno.hpengine.math.Vector4fStrukt
import org.joml.Vector3fc
import java.nio.FloatBuffer
import kotlin.math.min

fun drawLines(
    renderStateManager: RenderStateManager,
    programManager: ProgramManager,
    linesProgram: IProgram<LinesProgramUniforms>,
    vertices: PersistentTypedBuffer<Vector4fStrukt>,
    linePoints: List<Vector3fc>,
    lineWidth: Float = 5f,
    modelMatrix: FloatBuffer = identityMatrix4fBuffer,
    viewMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.viewMatrixAsBuffer,
    projectionMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.projectionMatrixAsBuffer,
    color: Vector3fc
) {

    if (linePoints.isEmpty()) return

    vertices.putLinesPoints(linePoints)

    drawLines(
        renderStateManager,
        programManager,
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

fun PersistentTypedBuffer<Vector4fStrukt>.putLinesPoints(linePoints: List<Vector3fc>) {
    ensureCapacityInBytes(linePoints.size * type.sizeInBytes)

    for (i in linePoints.indices) {
        this.typedBuffer.forIndex(i) { it.set(linePoints[i]) }
    }
}

fun drawLines(
    renderStateManager: RenderStateManager,
    programManager: ProgramManager,
    linesProgram: IProgram<LinesProgramUniforms>,
    vertices: PersistentTypedBuffer<Vector4fStrukt>,
    lineWidth: Float = 5f,
    verticesCount: Int,
    modelMatrix: FloatBuffer = identityMatrix4fBuffer,
    viewMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.viewMatrixAsBuffer,
    projectionMatrix: FloatBuffer = renderStateManager.renderState.currentReadState.camera.projectionMatrixAsBuffer,
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
    drawLines(min(lineWidth, programManager.gpuContext.maxLineWidth), verticesCount)
}

