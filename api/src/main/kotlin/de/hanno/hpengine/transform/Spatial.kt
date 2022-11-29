package de.hanno.hpengine.transform

import de.hanno.hpengine.camera.Frustum
import de.hanno.hpengine.lifecycle.Updatable
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc
import java.lang.Float.MAX_VALUE

interface Spatial : Updatable {
    val boundingVolume: AABB
    fun getCenter(transform: Matrix4f): Vector3f {
        boundingVolume.recalculate(transform)
        return boundingVolume.center
    }
    fun getBoundingVolume(transform: Matrix4f): AABB {
        boundingVolume.recalculate(transform)
        return boundingVolume
    }
    fun recalculate(transform: Matrix4f) {
        boundingVolume.recalculate(transform)
    }

    fun getBoundingSphereRadius(transform: Matrix4f): Float {
        boundingVolume.recalculate(transform)
        return boundingVolume.boundingSphereRadius
    }

    companion object {
        val MIN: Vector3fc = Vector3f(MAX_VALUE, MAX_VALUE, MAX_VALUE)
        val MAX: Vector3fc = Vector3f(-MAX_VALUE, -MAX_VALUE, -MAX_VALUE)

        fun isInFrustum(frustum: Frustum, centerWorld: Vector3f, minWorld: Vector3fc, maxWorld: Vector3fc): Boolean {
            val tempDistVector = Vector3f()
            Vector3f(minWorld).sub(maxWorld, tempDistVector)

            //		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
            //			de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
            //		if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
            //		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
            //				|| de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
            return frustum.sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, tempDistVector.length() / 2)
        }
    }
}
