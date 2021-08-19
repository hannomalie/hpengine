package de.hanno.hpengine.engine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.engine.math.Vector4fStrukt
import de.hanno.hpengine.engine.math.Vector4iStrukt
import de.hanno.hpengine.engine.vertexbuffer.DataChannelComponent.FloatThree
import de.hanno.hpengine.engine.vertexbuffer.DataChannelComponent.FloatTwo
import de.hanno.struct.Struct
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

typealias HpVector3f = de.hanno.hpengine.engine.math.Vector3f
typealias HpVector4f = de.hanno.hpengine.engine.math.Vector4f

interface VertexStruktPacked : Strukt {
    val ByteBuffer.position: Vector4fStrukt
    val ByteBuffer.texCoord: Vector4fStrukt
    val ByteBuffer.normal: Vector4fStrukt
    val ByteBuffer.dummy: Vector4fStrukt

    companion object
}

interface AnimatedVertexStruktPacked : Strukt {
    val ByteBuffer.position: Vector4fStrukt
    val ByteBuffer.texCoord: Vector4fStrukt
    val ByteBuffer.normal: Vector4fStrukt
    val ByteBuffer.weights: Vector4fStrukt

    val ByteBuffer.jointIndices: Vector4iStrukt
    val ByteBuffer.dummy: Vector4fStrukt
    val ByteBuffer.dummy1: Vector4fStrukt
    val ByteBuffer.dummy2: Vector4fStrukt

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
