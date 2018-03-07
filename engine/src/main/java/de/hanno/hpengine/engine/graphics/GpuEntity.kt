package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.transform.AABB
import org.joml.Matrix4f
import java.nio.ByteBuffer

data class GpuEntity(val trafo: Matrix4f,
                     val selected: Boolean,
                     val materialIndex: Int,
                     val update: Update,
                     val meshBufferIndex: Int,
                     val entityIndex: Int,
                     val meshIndex: Int,
                     val baseVertex: Int,
                     val baseJointIndex: Int,
                     val animationFrame0: Int,
                     val isInvertedTexCoordY: Boolean,
                     val aabb: AABB) : Bufferable {
    override fun getBytesPerObject(): Int {
        return getBytesPerInstance()
    }

    override fun putToBuffer(buffer: ByteBuffer) {
        buffer.putFloat(trafo.m00())
        buffer.putFloat(trafo.m01())
        buffer.putFloat(trafo.m02())
        buffer.putFloat(trafo.m03())
        buffer.putFloat(trafo.m10())
        buffer.putFloat(trafo.m11())
        buffer.putFloat(trafo.m12())
        buffer.putFloat(trafo.m13())
        buffer.putFloat(trafo.m20())
        buffer.putFloat(trafo.m21())
        buffer.putFloat(trafo.m22())
        buffer.putFloat(trafo.m23())
        buffer.putFloat(trafo.m30())
        buffer.putFloat(trafo.m31())
        buffer.putFloat(trafo.m32())
        buffer.putFloat(trafo.m33())

        buffer.putInt(if (selected) 1 else 0)
        buffer.putInt(materialIndex)
        buffer.putInt(update.asDouble.toInt())
        buffer.putInt(meshBufferIndex)

        buffer.putInt(entityIndex)
        buffer.putInt(meshIndex)
        buffer.putInt(baseVertex)
        buffer.putInt(baseJointIndex)

        buffer.putInt(animationFrame0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putInt(if (isInvertedTexCoordY) 1 else 0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putFloat(aabb.min.x)
        buffer.putFloat(aabb.min.y)
        buffer.putFloat(aabb.min.z)
        buffer.putFloat(1f)

        buffer.putFloat(aabb.max.x)
        buffer.putFloat(aabb.max.y)
        buffer.putFloat(aabb.max.z)
        buffer.putFloat(1f)
    }



    companion object {
        fun getBytesPerInstance(): Int {
            return 16 * java.lang.Float.BYTES + 16 * Integer.BYTES + 8 * java.lang.Float.BYTES
        }
    }
}