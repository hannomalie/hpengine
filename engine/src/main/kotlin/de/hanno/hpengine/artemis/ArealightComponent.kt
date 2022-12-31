package de.hanno.hpengine.artemis

import com.artemis.Component
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.TransformComponent
import org.joml.Vector3f

class AreaLightComponent: Component() {
    var color: Vector3f = Vector3f(1f)
    var scale: Vector3f = Vector3f(10f)
    // TDOO: Move to seperate component
    var camera = CameraComponent().apply {
        fov = 90f
        far = 250f
        near = 1f
        ratio = 1f
        perspective = true
    }
    var transform = TransformComponent()
    val width get() = scale.x
    val height get() = scale.y
    val range get() = scale.z
}