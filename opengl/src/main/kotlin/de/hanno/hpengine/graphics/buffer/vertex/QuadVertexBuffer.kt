package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.graphics.DataChannels
import de.hanno.hpengine.graphics.GraphicsApi
import org.joml.Vector2f
import java.util.*

private val DEFAULTCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.TEXCOORD,
    DataChannels.NORMAL
)

object QuadVertexBuffer {

    context(GraphicsApi)
    operator fun invoke(
        values: FloatArray = fullScreenVertices,
    ) = VertexBufferImpl(
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
