package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.util.Util
import org.joml.Matrix4f

class CameraComponent: Component() {
    var near: Float = 0.1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var far: Float = 2000f
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var fov: Float = Camera.Defaults.fov
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var ratio: Float = 1280f / 720f
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var exposure: Float = 5f
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var focalDepth: Float = Camera.Defaults.focalDepth
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var focalLength: Float = Camera.Defaults.focalLength
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var fStop: Float = Camera.Defaults.fStop
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var perspective = true
        set(value) {
            field = value
            updateProjectionMatrix()
        }
    var width = 1600f
        set(width) {
            field = width
            updateProjectionMatrix()
        }
    var height = 1600f
        set(height) {
            field = height
            updateProjectionMatrix()
        }

    var projectionMatrix: Matrix4f = Util.createPerspective(fov, ratio, near, far)

    private fun updateProjectionMatrix() {
        projectionMatrix = if (perspective) {
            Util.createPerspective(fov, ratio, near, far)
        } else {
            Util.createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
        }
    }
}