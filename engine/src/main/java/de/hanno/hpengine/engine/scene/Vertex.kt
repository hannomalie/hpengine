package de.hanno.hpengine.engine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.DataChannelComponent.FloatThree
import de.hanno.hpengine.engine.model.DataChannelComponent.FloatTwo
import de.hanno.hpengine.engine.model.DataChannelProvider
import de.hanno.struct.Struct
import org.joml.Vector2f
import org.joml.Vector3f
import java.nio.ByteBuffer

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
    companion object {
        val sizeInBytes = 3 * 4 * java.lang.Float.BYTES
    }
}
class AnimatedVertexStruct : Struct() {
    val position by HpVector3f()
    val texCoord by HpVector2f()
    val normal by HpVector3f()
    val weights by HpVector4f()
    val jointIndices by HpVector4i()

    companion object {
        val sizeInBytes = 16 * java.lang.Float.BYTES
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
