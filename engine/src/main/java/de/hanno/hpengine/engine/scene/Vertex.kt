package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import org.joml.Vector2f
import org.joml.Vector3f
import java.nio.ByteBuffer

data class Vertex(val position: Vector3f, val texCoord: Vector2f, val normal: Vector3f, val lightmapCoords : Vector3f): Bufferable {
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
                putFloat(lightmapCoords.x)
                putFloat(lightmapCoords.y)
                putFloat(lightmapCoords.z)
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
            lightmapCoords.x = it.float
            lightmapCoords.y = it.float
            lightmapCoords.z = it.float
        }

    }

    override fun getBytesPerObject(): Int = 4 * 11
}