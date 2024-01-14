package de.hanno.hpengine.graphics.light.area

import com.artemis.Component
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.TransformComponent
import org.joml.Vector3f

class AreaLightComponent: Component() {
    var color: Vector3f = Vector3f(1f)
    var scale: Vector3f = Vector3f(10f)
    // TDOO: Move to seperate component
    var camera = CameraComponent().apply {
        camera.fov = 90f
        camera.far = 250f
        camera.near = 1f
        camera.ratio = 1f
        camera.perspective = true
    }
    var transform = TransformComponent()
    val width get() = scale.x
    val height get() = scale.y
    val range get() = scale.z
}