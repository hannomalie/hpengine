package de.hanno.hpengine.transform

import de.hanno.hpengine.Transform
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

open class SimpleSpatial(override val boundingVolume: AABB = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))) : Serializable,
    Spatial

inline val SimpleSpatial.boundingSphereRadius: Float
    get() = boundingVolume.boundingSphereRadius

open class TransformSpatial(val transform: Transform, _boundingVolume: AABB) : SimpleSpatial(_boundingVolume) {
    inline val center: Vector3f
        get() = boundingVolume.center
}
class StaticTransformSpatial(transform: Transform, val aabb: AABB) : TransformSpatial(transform, aabb) {
    init {
        recalculate(transform)
    }
    override fun update(deltaSeconds: Float) { }
    override fun getBoundingVolume(transform: Matrix4f): AABB = boundingVolume
}
