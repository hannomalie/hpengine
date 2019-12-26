package de.hanno.hpengine.engine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.engine.vertexbuffer.DataChannelComponent.FloatThree
import de.hanno.hpengine.engine.vertexbuffer.DataChannelComponent.FloatTwo
import de.hanno.struct.Struct
import org.joml.Vector2f
import org.joml.Vector3f

typealias HpVector3f = de.hanno.hpengine.engine.math.Vector3f
typealias HpVector4f = de.hanno.hpengine.engine.math.Vector4f
typealias HpVector4i = de.hanno.hpengine.engine.math.Vector4i
typealias HpVector2f = de.hanno.hpengine.engine.math.Vector2f
class VertexStruct : Struct() {
    val position by HpVector3f()
    val texCoord by HpVector2f()
    val normal by HpVector3f()
    companion object {
        val sizeInBytes = 8 * java.lang.Float.BYTES
    }
}
class VertexStructPacked: Struct() {
    val position by HpVector4f()
    val texCoord by HpVector4f()
    val normal by HpVector4f()
    val dummy by HpVector4f()
    override fun toString(): String {
        return "VertexStructPacked(position=(${position.x}, ${position.y}, ${position.z}), " +
                "texCoord=(${texCoord.x}, ${texCoord.y}), normal=(${normal.x}, ${normal.y}, ${normal.z}))"
    }
    companion object {
        val sizeInBytes = 4 * 4 * java.lang.Float.BYTES
    }
}
class AnimatedVertexStruct : Struct() {
    val position by HpVector3f()
    val texCoord by HpVector2f()
    val normal by HpVector3f()
    val weights by HpVector4f()

    val jointIndices by HpVector4i()

    companion object {
        val sizeInBytes = AnimatedVertexStruct().sizeInBytes
    }
}
class AnimatedVertexStructPacked : Struct() {
    val position by HpVector4f()
    val texCoord by HpVector4f()
    val normal by HpVector4f()
    val weights by HpVector4f()
    val jointIndices by HpVector4i()
    val dummy by HpVector4f()
    val dummy1 by HpVector4f()
    val dummy2 by HpVector4f()

    companion object {
        val sizeInBytes = AnimatedVertexStructPacked().sizeInBytes

    }
}

data class Vertex (val position: Vector3f = Vector3f(),
                  val texCoord: Vector2f = Vector2f(),
                  val normal: Vector3f = Vector3f()) {

    companion object {
        val channels by lazy {
            ImmutableSet.of(FloatThree("position", "vec3"), FloatTwo("texCoord", "vec2"), FloatThree("normal", "vec3"))
        }
        val sizeInBytes by lazy {
            channels.map { it.byteSize }.reduce { a, b -> a + b }
        }
    }
}
