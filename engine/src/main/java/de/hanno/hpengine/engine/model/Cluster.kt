package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import kotlinx.coroutines.CoroutineScope
import org.joml.FrustumIntersection
import org.joml.Vector3f
import java.util.ArrayList


class Cluster(val customIsCulled: Cluster.(Camera) -> Boolean = { defaultIsCulled(it) }) : ArrayList<Instance>(), Updatable {
    var boundingVolume: AABB = AABB(AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX)))
        private set

    var recalculatedInCycle = -1L
        private set

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        (0 until size).map { i ->
            with(get(i)) {
                update(scene, deltaSeconds)
            }
        }
        recalculate(scene.currentCycle)
    }

    private fun recalculate(currentCycle: Long) {
        if(all { it.spatial is StaticTransformSpatial } && (boundingVolume.min != Vector3f(Spatial.MAX) || boundingVolume.max != Vector3f(Spatial.MIN))) {
            return
        }
        boundingVolume.localAABB = map { it.boundingVolume }.getSurroundingAABB()
        recalculatedInCycle = currentCycle
    }

    fun isCulled(camera: Camera): Boolean {
        return customIsCulled(camera)
    }

}
fun Cluster.defaultIsCulled(camera: Camera, distanceMultiplierMin: Float = 0f, distanceMultiplierMax: Float = 4f): Boolean {
    val intersection = camera.frustum.frustumIntersection.intersectAab(boundingVolume.min, boundingVolume.max)
    val clusterIsInFrustum = intersection == FrustumIntersection.INTERSECT || intersection == FrustumIntersection.INSIDE
    val distanceToClusterCenter = camera.getPosition().distance(boundingVolume.center)
    val clusterNearEnough = distanceToClusterCenter < distanceMultiplierMax * boundingVolume.boundingSphereRadius
            && distanceToClusterCenter > distanceMultiplierMin * boundingVolume.boundingSphereRadius
    return !(clusterIsInFrustum && clusterNearEnough)
}