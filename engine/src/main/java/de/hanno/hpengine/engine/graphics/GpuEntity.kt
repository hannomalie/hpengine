package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.math.Vector3fStrukt
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.BoundingSphere
import de.hanno.hpengine.engine.transform.BoundingVolume
import org.joml.Matrix4fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

typealias HpMatrix = de.hanno.hpengine.engine.math.Matrix4f

enum class BoundingVolumeType(val value: Float) {
    AABB(0f),
    Sphere(1f)
}

interface EntityStrukt : Strukt {
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
