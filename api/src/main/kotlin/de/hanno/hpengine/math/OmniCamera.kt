package de.hanno.hpengine.math

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import org.joml.Quaternionf
import org.joml.Vector3f

data class OmniCamera(val position: Vector3f) {
    val cameras = (0 until 6).map { index ->
        Camera(Transform()).apply {
            updateCameraAtIndexForPosition(index, position)
        }
    }

    fun updatePosition(position: Vector3f) {
        this.position.set(position)

        cameras.forEachIndexed { index, it ->
            it.updateCameraAtIndexForPosition(index, position)
            it.transform.position.set(position)
            it.update()
        }
    }
    private fun Camera.updateCameraAtIndexForPosition(index: Int, position: Vector3f) {
        transform.rotation(Quaternionf().identity())
        when (index) {
            0 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
                transform.rotate(Vector3f(0f, 1f, 0f), 90)
            }
            1 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
                transform.rotate(Vector3f(0f, 1f, 0f), -90)
            }
            2 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
                transform.rotate(Vector3f(1f, 0f, 0f), 90)
                transform.rotate(Vector3f(0f, 1f, 0f), 180)

            }
            3 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
                transform.rotate(Vector3f(1f, 0f, 0f), -90)
                transform.rotate(Vector3f(0f, 1f, 0f), 180)
            }
            4 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
                transform.rotate(Vector3f(0f, 1f, 0f), -180)
            }
            5 -> {
                transform.rotate(Vector3f(0f, 0f, 1f), 180)
            }
        }
        transform.translateLocal(position)
    }
}