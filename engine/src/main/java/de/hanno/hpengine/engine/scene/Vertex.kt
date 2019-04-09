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
}
class AnimatedVertexStruct : Struct() {
    val position by HpVector3f()
    val texCoord by HpVector2f()
    val normal by HpVector3f()
    val weights by HpVector4f()
    val jointIndices by HpVector4i()
}

data class Vertex (override val name: String = "Vertex",
                  val position: Vector3f = Vector3f(),
                  val texCoord: Vector2f = Vector2f(),
                  val normal: Vector3f = Vector3f()) : Bufferable, DataChannelProvider {

    override fun getBytesPerObject(): Int {
        return sizeInBytes
    }

    override fun putToBuffer(buffer: ByteBuffer?) {
        buffer?.let {
            with(buffer) {
                putFloat(position.x)
                putFloat(position.y)
                putFloat(position.z)
                putFloat(texCoord.x)
                putFloat(texCoord.y)
                putFloat(normal.x)
                putFloat(normal.y)
                putFloat(normal.z)
            }
        }
    }

    override fun getFromBuffer(buffer: ByteBuffer?) {
        buffer?.let {
            position.x = it.float
            position.y = it.float
            position.z = it.float
            texCoord.x = it.float
            texCoord.y = it.float
            normal.x = it.float
            normal.y = it.float
            normal.z = it.float
        }
    }

    companion object {
        val channels by lazy {
            ImmutableSet.of(FloatThree("position", "vec3"), FloatTwo("texCoord", "vec2"), FloatThree("normal", "vec3"))
        }
        val sizeInBytes by lazy {
            channels.map { it.byteSize }.reduce { a, b -> a + b }
        }
    }
    override val channels = Vertex.channels

    private val sizeInBytes = Vertex.sizeInBytes
}