package de.hanno.hpengine.artemis

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.imgui.editor.primaryCamera
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

class CameraSystem: BaseSystem(), WorldPopulator {
    override fun processSystem() { }
    override fun World.populate() {
        addPrimaryCamera()
    }
}

fun World.addPrimaryCamera() {
    edit(create()).apply {
        create(TransformComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "PrimaryCamera"
        }
        create(CameraComponent::class.java)

        getSystem(TagManager::class.java).register(primaryCamera, entityId)
    }
}