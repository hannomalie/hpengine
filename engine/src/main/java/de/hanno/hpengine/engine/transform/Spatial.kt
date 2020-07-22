package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.component.ModelComponent
import kotlinx.coroutines.CoroutineScope
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

open class SimpleSpatial(override val minMax: AABB = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))) : Serializable, Spatial {
    override val boundingSphereRadius: Float
        get() = minMax.boundingSphereRadius
}

open class TransformSpatial(val transform: Transform<*>, val xxx: AABB) : SimpleSpatial(xxx) {
    inline val center: Vector3f
        get() = xxx.center

}
open class StaticTransformSpatial(transform: Transform<*>, val modelComponent: ModelComponent) : TransformSpatial(transform, modelComponent.minMax) {
    override fun CoroutineScope.update(deltaSeconds: Float) = minMax.recalculate(transform)
}
// TODO: Is this still needed?
open class AnimatedTransformSpatial(transform: Transform<*>, modelComponent: ModelComponent) : StaticTransformSpatial(transform, modelComponent)
