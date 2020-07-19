package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.lifecycle.Updatable
import org.joml.Vector3f
import org.joml.Vector3fc

import java.lang.Float.MAX_VALUE

interface Spatial : Updatable {
    fun getCenter(transform: Transform<*>): Vector3f
    fun getMinMax(transform: Transform<*>): AABB

    val boundingSphereRadius: Float
    fun getBoundingSphereRadius(transform: Transform<*>): Float

    companion object {
        val MIN: Vector3fc = Vector3f(MAX_VALUE, MAX_VALUE, MAX_VALUE)
        val MAX: Vector3fc = Vector3f(-MAX_VALUE, -MAX_VALUE, -MAX_VALUE)

        fun isInFrustum(camera: Camera, centerWorld: Vector3f, minWorld: Vector3f, maxWorld: Vector3f): Boolean {
            val tempDistVector = Vector3f()
            Vector3f(minWorld).sub(maxWorld, tempDistVector)

            //		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
            //			de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
            //		if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
            //		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
            //				|| de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
            return camera.frustum.sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, tempDistVector.length() / 2)
        }
    }
}
