package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.hpengine.engine.math.Vector3fStrukt
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.BoundingSphere
import de.hanno.hpengine.engine.transform.BoundingVolume
import de.hanno.struct.Struct
import org.joml.Matrix4f
import org.joml.Matrix4fc
import java.nio.ByteBuffer

typealias HpMatrix = de.hanno.hpengine.engine.math.Matrix4f

enum class BoundingVolumeType(val value: Float) {
    AABB(0f),
    Sphere(1f)
}
class EntityStruct : Struct() {
    val trafo by HpMatrix()
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
    val min by Vector3f()
    var boundingVolumeType by BoundingVolumeType::class.java
    val max by Vector3f()
    var dummy4 by 0

    fun setTrafoAndBoundingVolume(source: Matrix4f, boundingVolume: BoundingVolume) {
        trafo.set(source)
        when(boundingVolume) {
            is AABB -> {
                this.boundingVolumeType = BoundingVolumeType.AABB
                this.min.set(boundingVolume.min)
                this.max.set(boundingVolume.max)
            }
            is BoundingSphere -> {
                this.boundingVolumeType = BoundingVolumeType.Sphere
                this.min.set(boundingVolume.positionRadius)
                this.max.set(boundingVolume.positionRadius)
            }
        }.let { }
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

interface EntityStrukt : struktgen.api.Strukt {
    val ByteBuffer.trafo: Matrix4fStrukt
    var ByteBuffer.selected: Boolean
    var ByteBuffer.materialIndex: Int
    var ByteBuffer.update: Int
    var ByteBuffer.meshBufferIndex: Int
    var ByteBuffer.entityIndex: Int
    var ByteBuffer.meshIndex: Int
    var ByteBuffer.baseVertex: Int
    var ByteBuffer.baseJointIndex: Int
    var ByteBuffer.animationFrame0: Int
    val ByteBuffer.animationFrame1: Int
    val ByteBuffer.animationFrame2: Int
    val ByteBuffer.animationFrame3: Int
    var ByteBuffer.isInvertedTexCoordY: Int
    val ByteBuffer.dummy0: Int
    val ByteBuffer.dummy1: Int
    val ByteBuffer.dummy2: Int
    val ByteBuffer.min: Vector3fStrukt
    var ByteBuffer.boundingVolumeType: BoundingVolumeType
    val ByteBuffer.max: Vector3fStrukt
    var ByteBuffer.dummy4: Int

    fun ByteBuffer.setTrafoAndBoundingVolume(source: Matrix4fc, boundingVolume: BoundingVolume) {
        trafo.set(this, source)
        when(boundingVolume) {
            is AABB -> {
                boundingVolumeType = BoundingVolumeType.AABB
                min.set(this, boundingVolume.min)
                max.set(this, boundingVolume.max)
            }
            is BoundingSphere -> {
                boundingVolumeType = BoundingVolumeType.Sphere
                min.set(this, boundingVolume.positionRadius)
                max.set(this, boundingVolume.positionRadius)
            }
        }.let { }
    }
    companion object
}
