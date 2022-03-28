package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.StaticModel
import org.joml.Vector3f
import java.io.Serializable

open class SimpleSpatial(override val boundingVolume: AABB = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))) : Serializable, Spatial

inline val SimpleSpatial.boundingSphereRadius: Float
    get() = boundingVolume.boundingSphereRadius

open class TransformSpatial(val transform: Transform, _boundingVolume: AABB) : SimpleSpatial(_boundingVolume) {
    inline val center: Vector3f
        get() = boundingVolume.center
}
class StaticTransformSpatial(transform: Transform, val model: StaticModel) : TransformSpatial(transform, AABB(model.boundingVolume.localAABB)) {
    override fun update(deltaSeconds: Float) = boundingVolume.recalculate(transform)
}
// TODO: Is this still needed?
class AnimatedTransformSpatial(transform: Transform, model: AnimatedModel) : TransformSpatial(transform, AABB(model.boundingVolume.localAABB)) {
    override fun update(deltaSeconds: Float) = boundingVolume.recalculate(transform)
}
