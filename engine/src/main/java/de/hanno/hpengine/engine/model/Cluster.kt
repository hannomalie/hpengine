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
import kotlin.math.max
import kotlin.math.pow


class Cluster : ArrayList<Instance>(), Updatable {
    var boundingVolume: AABB = AABB(AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX)))
        private set
    var allStaticInstances = true
        private set
    var updatedInCycle = -1L
        internal set

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        for (i in 0 until size) {
            get(i).run {
                update(scene, deltaSeconds)
            }
        }
        recalculate(scene.currentCycle)
    }

    override fun add(element: Instance): Boolean {
        if(element.spatial !is StaticTransformSpatial) allStaticInstances = false
        return super.add(element).apply {
            boundingVolume.localAABB = getSurroundingAABB()
        }
    }
    override fun addAll(elements: Collection<Instance>): Boolean {
        if(any { it.spatial !is StaticTransformSpatial }) allStaticInstances = false
        return super.addAll(elements).apply {
            boundingVolume.localAABB = getSurroundingAABB()
        }
    }
    override fun addAll(index: Int, elements: Collection<Instance>): Boolean {
        if(any { it.spatial !is StaticTransformSpatial }) allStaticInstances = false
        return super.addAll(index, elements).apply {
            boundingVolume.localAABB = getSurroundingAABB()
        }
    }
    override fun add(index: Int, element: Instance) {
        if(element.spatial !is StaticTransformSpatial) allStaticInstances = false
        super.add(index, element)
        boundingVolume.localAABB = getSurroundingAABB()
    }

    private fun recalculate(currentCycle: Long) {
        if(allStaticInstances) return

        boundingVolume.localAABB = getSurroundingAABB()
        updatedInCycle = currentCycle
    }

    fun instanceCountToDraw(camera: Camera): Int {
        val intersection = camera.frustum.frustumIntersection.intersectAab(boundingVolume.min, boundingVolume.max)
        val clusterIsInFrustum = intersection == FrustumIntersection.INTERSECT || intersection == FrustumIntersection.INSIDE
        val distanceToClusterCenter = camera.getPosition().distance(boundingVolume.center)
        val maxDistance = boundingVolume.boundingSphereRadius * 6f
        val minDistance = boundingVolume.boundingSphereRadius

        if (!clusterIsInFrustum) return 0
        if(distanceToClusterCenter < minDistance) return size

        val percent = 1f - (distanceToClusterCenter / maxDistance)
        return max(0, (percent * size.toFloat()).toInt())
    }

}