package de.hanno.hpengine.model

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.lifecycle.Updatable
import de.hanno.hpengine.transform.*
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import org.joml.FrustumIntersection
import org.joml.Vector3f
import java.util.ArrayList
import kotlin.math.max


class Cluster : ArrayList<Instance>(), Updatable {
    var boundingVolume: AABB = AABB(AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX)))
        private set
    var allStaticInstances = true
        private set
    var updatedInCycle = -1L
        internal set

    override fun update(deltaSeconds: Float) {
        for (i in 0 until size) {
            this[i].update(deltaSeconds)
        }
        recalculate()
    }

    override fun add(element: Instance): Boolean {
        if(element.spatial !is StaticTransformSpatial) allStaticInstances = false
        return super.add(element).apply {
            boundingVolume.localAABB.setFrom(getSurroundingAABB())
        }
    }
    override fun addAll(elements: Collection<Instance>): Boolean {
        if(any { it.spatial !is StaticTransformSpatial }) allStaticInstances = false
        return super.addAll(elements).apply {
            boundingVolume.localAABB.setFrom(getSurroundingAABB())
        }
    }
    override fun addAll(index: Int, elements: Collection<Instance>): Boolean {
        if(any { it.spatial !is StaticTransformSpatial }) allStaticInstances = false
        return super.addAll(index, elements).apply {
            boundingVolume.localAABB.setFrom(getSurroundingAABB())
        }
    }
    override fun add(index: Int, element: Instance) {
        if(element.spatial !is StaticTransformSpatial) allStaticInstances = false
        super.add(index, element)
        boundingVolume.localAABB.setFrom(getSurroundingAABB())
    }

    private fun recalculate() {
        if(allStaticInstances) return

        boundingVolume.localAABB.setFrom(getSurroundingAABB())
    }

    fun instanceCountToDraw(camera: Camera): Int {
        val intersection = camera.frustum.frustumIntersection.intersectAab(boundingVolume.min, boundingVolume.max)
        val clusterIsInFrustum = intersection == FrustumIntersection.INTERSECT || intersection == FrustumIntersection.INSIDE
        val distanceToClusterCenter = camera.getPosition().distance(boundingVolume.center)
        val maxDistance = boundingVolume.boundingSphereRadius * 6f
        val minDistance = boundingVolume.boundingSphereRadius

        if (!clusterIsInFrustum) return 0
        if(distanceToClusterCenter < minDistance
                || distanceToClusterCenter == Float.NEGATIVE_INFINITY
                || distanceToClusterCenter == Float.POSITIVE_INFINITY) return size

        val percent = 1f - (distanceToClusterCenter / maxDistance)
        return max(0, (percent * size.toFloat()).toInt())
    }

}
