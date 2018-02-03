package de.hanno.hpengine.engine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.DataChannelComponent
import de.hanno.hpengine.engine.model.DataChannelComponent.*
import de.hanno.hpengine.engine.model.DataChannelProvider
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4i
import java.nio.ByteBuffer
import java.util.ArrayList

data class AnimatedVertex (override val name: String = "Vertex",
                  val position: Vector3f = Vector3f(),
                  val texCoord: Vector2f = Vector2f(),
                  val normal: Vector3f = Vector3f(),
                  val weights: Vector4f,
                  val jointIndices: Vector4i) : Bufferable, DataChannelProvider {

    override fun getBytesPerObject(): Int {
        return byteSize
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
                putFloat(weights.x)
                putFloat(weights.y)
                putFloat(weights.z)
                putFloat(weights.w)
                putInt(jointIndices.x)
                putInt(jointIndices.y)
                putInt(jointIndices.z)
                putInt(jointIndices.w)
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
            weights.x = it.float
            weights.y = it.float
            weights.z = it.float
            weights.w = it.float
            jointIndices.x = it.int
            jointIndices.y = it.int
            jointIndices.z = it.int
            jointIndices.w = it.int
        }
    }

    override val channels by lazy {
        ImmutableSet.of(
            FloatThree("position", "vec3"),
            FloatTwo("texCoord", "vec2"),
            FloatThree("normal", "vec3"),
            FloatFour("weights", "vec4"),
            IntFour("jointIndices", "ivec4"))
    }

    private val byteSize by lazy {
        channels.map { it.byteSize }.reduce { a, b -> a + b }
    }
}