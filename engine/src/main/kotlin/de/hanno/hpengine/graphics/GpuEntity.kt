package de.hanno.hpengine.graphics

import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.math.Vector3fStrukt
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.BoundingSphere
import de.hanno.hpengine.transform.BoundingVolume
import org.joml.Matrix4fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

enum class BoundingVolumeType(val value: Float) {
    AABB(0f),
    Sphere(1f)
}

interface EntityStrukt : Strukt {
    context(ByteBuffer) val trafo: Matrix4fStrukt
    context(ByteBuffer) var selected: Boolean
    context(ByteBuffer) var materialIndex: Int
    context(ByteBuffer) var update: Int
    context(ByteBuffer) var meshBufferIndex: Int
    context(ByteBuffer) var entityIndex: Int
    context(ByteBuffer) var meshIndex: Int
    context(ByteBuffer) var baseVertex: Int
    context(ByteBuffer) var baseJointIndex: Int
    context(ByteBuffer) var animationFrame0: Int
    context(ByteBuffer) val animationFrame1: Int
    context(ByteBuffer) val animationFrame2: Int
    context(ByteBuffer) val animationFrame3: Int
    context(ByteBuffer) var isInvertedTexCoordY: Int
    context(ByteBuffer) val dummy0: Int
    context(ByteBuffer) val dummy1: Int
    context(ByteBuffer) val dummy2: Int
    context(ByteBuffer) val min: Vector3fStrukt
    context(ByteBuffer) var boundingVolumeType: BoundingVolumeType
    context(ByteBuffer) val max: Vector3fStrukt
    context(ByteBuffer) var dummy4: Int

    context(ByteBuffer)
    fun ByteBuffer.setTrafoAndBoundingVolume(source: Matrix4fc, boundingVolume: BoundingVolume) {
        trafo.set(source)
        when(boundingVolume) {
            is AABB -> {
                boundingVolumeType = BoundingVolumeType.AABB
                min.set(boundingVolume.min)
                max.set(boundingVolume.max)
            }
            is BoundingSphere -> {
                boundingVolumeType = BoundingVolumeType.Sphere
                min.set(boundingVolume.positionRadius)
                max.set(boundingVolume.positionRadius)
            }
        }.let { }
    }
    companion object
}
