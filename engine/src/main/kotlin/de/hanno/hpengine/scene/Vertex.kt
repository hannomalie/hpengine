package de.hanno.hpengine.scene

import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.math.Vector4iStrukt
import de.hanno.hpengine.graphics.buffer.vertex.DataChannelComponent.FloatThree
import de.hanno.hpengine.graphics.buffer.vertex.DataChannelComponent.FloatTwo
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface VertexStruktPacked : Strukt {
    context(ByteBuffer) val position: Vector4fStrukt
    context(ByteBuffer) val texCoord: Vector4fStrukt
    context(ByteBuffer) val normal: Vector4fStrukt
    context(ByteBuffer) val dummy: Vector4fStrukt

    companion object
}

interface AnimatedVertexStruktPacked : Strukt {
    context(ByteBuffer) val position: Vector4fStrukt
    context(ByteBuffer) val texCoord: Vector4fStrukt
    context(ByteBuffer) val normal: Vector4fStrukt
    context(ByteBuffer) val weights: Vector4fStrukt

    context(ByteBuffer) val jointIndices: Vector4iStrukt
    context(ByteBuffer) val dummy: Vector4fStrukt
    context(ByteBuffer) val dummy1: Vector4fStrukt
    context(ByteBuffer) val dummy2: Vector4fStrukt

    companion object
}

interface BaseVertex {
    val position: Vector3fc
    val texCoord: Vector2fc
    val normal: Vector3fc
}
data class Vertex(
    override val position: Vector3fc = Vector3f(),
    override val texCoord: Vector2fc = Vector2f(),
    override val normal: Vector3fc = Vector3f()
): BaseVertex {

    companion object {
        val channels by lazy {
            setOf(FloatThree("position", "vec3"), FloatTwo("texCoord", "vec2"), FloatThree("normal", "vec3"))
        }
        val sizeInBytes by lazy {
            channels.map { it.byteSize }.reduce { a, b -> a + b }
        }
    }
}
