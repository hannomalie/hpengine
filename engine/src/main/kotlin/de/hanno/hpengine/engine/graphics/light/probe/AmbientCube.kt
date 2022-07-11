package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.texture.CubeMap
import org.joml.Vector3f
import org.lwjgl.opengl.GL13
import java.nio.ByteBuffer

class AmbientCube(val position: Vector3f, val cubeMap: CubeMap, val distanceMap: CubeMap, val index: Int): Bufferable, Comparable<AmbientCube> {
    override fun compareTo(other: AmbientCube) = index.compareTo(other.index)

    override fun putToBuffer(buffer: ByteBuffer) {
        buffer.putFloat(position.x)
        buffer.putFloat(position.y)
        buffer.putFloat(position.z)
        buffer.putFloat(-1f)
        buffer.putDouble(java.lang.Double.longBitsToDouble(cubeMap.handle))
        buffer.putDouble(java.lang.Double.longBitsToDouble(distanceMap.handle))
    }

    override val bytesPerObject get() = sizeInBytes

    override fun debugPrintFromBuffer(buffer: ByteBuffer): String {
        return staticDebugPrintFromBuffer(buffer)
    }

    companion object {
        const val sizeInBytes = java.lang.Float.BYTES * 4 + java.lang.Double.BYTES * 2

        fun staticDebugPrintFromBuffer(buffer: ByteBuffer): String {
            val builder = StringBuilder()

            builder.appendln("position.x = ${buffer.float}")
            builder.appendln("position.y = ${buffer.float}")
            builder.appendln("position.z = ${buffer.float}")
            builder.appendln("filler = ${buffer.float}")
            builder.appendln("handle = ${buffer.double}")
            builder.appendln("filler = ${buffer.double}")

            val resultString = builder.toString()
            println(resultString)
            return resultString
        }
    }
}

sealed class CubemapSide(val value: Int) {
    object PositiveX : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X)
    object NegativeX : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X)
    object PositiveY : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y)
    object NegativeY : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y)
    object PositiveZ : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z)
    object NegativeZ : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z)
}
