package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.struct.Struct
import de.hanno.struct.copyFrom
import org.joml.Matrix4f
import java.nio.ByteBuffer

typealias HpMatrix = de.hanno.hpengine.engine.math.Matrix4f

class GpuEntityStruct(parent: Struct? = null): Struct(parent) {
    val trafo by HpMatrix(this)
    var selected by false
    var materialIndex by 0
    var update by 0
    var meshBufferIndex by 0
    var entityIndex by 0
    var meshIndex by 0
    var baseVertex by 0
    var baseJointIndex by 0
    var animationFrame0 by 0
    private val animationFrame1 by 0
    private val animationFrame2 by 0
    private val animationFrame3 by 0
    var isInvertedTexCoordY by 0
    val dummy0 by 0
    val dummy1 by 0
    val dummy2 by 0
    val min by Vector3f(this)
    var dummy3 by 0.0f
    val max by Vector3f(this)
    var dummy4 by 0.0f

    fun setTrafoMinMax(source: Matrix4f, min: org.joml.Vector3f, max: org.joml.Vector3f) {
        val baseByteOffset = baseByteOffset
        source.get((baseByteOffset + trafo.localByteOffset).toInt(), buffer)
        min.get((baseByteOffset + this.min.localByteOffset).toInt(), buffer)
        max.get((baseByteOffset + this.max.localByteOffset).toInt(), buffer)
    }

    override fun toString(): String {
        return """trafo:
                |$trafo
                |entity: $entityIndex
                |material: $materialIndex
                |baseVertex: $baseVertex
                """.trimMargin()
    }

    companion object {
        fun getBytesPerInstance(): Int {
            return 16 * java.lang.Float.BYTES + 16 * Integer.BYTES + 8 * java.lang.Float.BYTES
        }
    }
}

data class GpuEntity(var trafo: Matrix4f,
                     var selected: Boolean,
                     var materialIndex: Int,
                     var update: Update,
                     var meshBufferIndex: Int,
                     var entityIndex: Int,
                     var meshIndex: Int,
                     var baseVertex: Int,
                     var baseJointIndex: Int,
                     var animationFrame0: Int,
                     var isInvertedTexCoordY: Boolean,
                     var aabb: AABB) : Bufferable {
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