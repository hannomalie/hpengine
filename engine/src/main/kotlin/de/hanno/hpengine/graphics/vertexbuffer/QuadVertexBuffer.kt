package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.graphics.DEFAULTCHANNELS
import de.hanno.hpengine.graphics.GpuContext
import javax.vecmath.Vector2f

object QuadVertexBuffer {

    context(GpuContext)
    operator fun invoke(
        values: FloatArray = fullScreenVertices,
    ) = VertexBuffer(
        DEFAULTCHANNELS,
        values
    )

    val fullScreenVertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f, 0f, 0f,
        1.0f, -1.0f, 0.0f, 1f, 0f,
        -1.0f, 1.0f, 0.0f, 0f, 1.0f,
        -1.0f, 1.0f, 0.0f, 0f, 1.0f,
        1.0f, -1.0f, 0.0f, 1.0f, 0f,
        1.0f, 1.0f, 0.0f, 1.0f, 1.0f
    )
    val quarterScreenVertices = getPositionsAndTexCoords(
        Vector2f(-1f, -1f),
        Vector2f(0f, 0f)
    )

    fun getPositionsAndTexCoords(leftBottom: Vector2f, rightUpper: Vector2f) = floatArrayOf(
        leftBottom.x, leftBottom.y, 0.0f, 0f, 0f,
        rightUpper.x, leftBottom.y, 0.0f, 1f, 0f,
        leftBottom.x, rightUpper.y, 0.0f, 0f, 1.0f,
        leftBottom.x, rightUpper.y, 0.0f, 0f, 1.0f,
        rightUpper.x, leftBottom.y, 0.0f, 1.0f, 0f,
        rightUpper.x, rightUpper.y, 0.0f, 1.0f, 1.0f
    )
}
