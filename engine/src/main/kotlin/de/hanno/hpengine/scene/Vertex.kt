package de.hanno.hpengine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.math.Vector4f
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.math.Vector4iStrukt
import de.hanno.hpengine.graphics.vertexbuffer.DataChannelComponent.FloatThree
import de.hanno.hpengine.graphics.vertexbuffer.DataChannelComponent.FloatTwo
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

typealias HpVector3f = de.hanno.hpengine.math.Vector3f
typealias HpVector4f = Vector4f

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

data class Vertex(
    val position: Vector3fc = Vector3f(),
    val texCoord: Vector2fc = Vector2f(),
    val normal: Vector3fc = Vector3f()
) {

    companion object {
        val channels by lazy {
            ImmutableSet.of(FloatThree("position", "vec3"), FloatTwo("texCoord", "vec2"), FloatThree("normal", "vec3"))
        }
        val sizeInBytes by lazy {
            channels.map { it.byteSize }.reduce { a, b -> a + b }
        }
    }
}
