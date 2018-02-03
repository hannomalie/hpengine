package de.hanno.hpengine.engine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.DataChannelProvider
import de.hanno.hpengine.engine.model.DataChannelComponent.FloatThree
import de.hanno.hpengine.engine.model.DataChannelComponent.FloatTwo
import org.joml.Vector2f
import org.joml.Vector3f
import java.nio.ByteBuffer

data class Vertex (override val name: String = "Vertex",
                  val position: Vector3f = Vector3f(),
                  val texCoord: Vector2f = Vector2f(),
                  val normal: Vector3f = Vector3f()) : Bufferable, DataChannelProvider {

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

    override val channels by lazy {
        ImmutableSet.of(FloatThree("position", "vec3"), FloatTwo("texCoord", "vec2"), FloatThree("normal", "vec3"))
    }

    private val byteSize by lazy {
        channels.map { it.byteSize }.reduce { a, b -> a + b }
    }
}