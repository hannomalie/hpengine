package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.BoundingSphere
import de.hanno.hpengine.engine.transform.BoundingVolume
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import de.hanno.struct.Struct
import org.joml.Matrix4f
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
