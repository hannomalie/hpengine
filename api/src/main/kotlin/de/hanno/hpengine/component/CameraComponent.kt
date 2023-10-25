package de.hanno.hpengine.component

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.math.createOrthogonal
import de.hanno.hpengine.math.createPerspective
import org.joml.Matrix4f
import org.koin.core.annotation.Single

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

    var projectionMatrix: Matrix4f = createPerspective(fov, ratio, near, far)

    private fun updateProjectionMatrix() {
        projectionMatrix = if (perspective) {
            createPerspective(fov, ratio, near, far)
        } else {
            createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
        }
    }
}

@Single(binds=[BaseSystem::class, CameraSystem::class])
class CameraSystem : BaseSystem(), WorldPopulator {
    override fun processSystem() { }
    override fun World.populate() {
        addPrimaryCamera()
    }
}

const val primaryCameraTag = "PRIMARY_CAMERA"

fun World.addPrimaryCamera() {
    edit(create()).apply {
        create(TransformComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "PrimaryCamera"
        }
        create(CameraComponent::class.java)

        getSystem(TagManager::class.java).register(primaryCameraTag, entityId)
    }
}