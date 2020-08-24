package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.io.Serializable

open class SimpleSpatial(override val boundingVolume: AABB = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))) : Serializable, Spatial

inline val SimpleSpatial.boundingSphereRadius: Float
    get() = boundingVolume.boundingSphereRadius

open class TransformSpatial(val transform: Transform, _boundingVolume: AABB) : SimpleSpatial(_boundingVolume) {
    inline val center: Vector3f
        get() = boundingVolume.center

}
open class StaticTransformSpatial(transform: Transform, val modelComponent: ModelComponent) : TransformSpatial(transform, modelComponent.boundingVolume) {
    override fun CoroutineScope.update(scene:Scene, deltaSeconds: Float) = boundingVolume.recalculate(transform)
}
// TODO: Is this still needed?
open class AnimatedTransformSpatial(transform: Transform, modelComponent: ModelComponent) : StaticTransformSpatial(transform, modelComponent)
