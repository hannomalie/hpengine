package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import de.hanno.hpengine.engine.transform.Transform
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f

class DirectionalLightComponent(
    val transform: Transform = Transform().apply {
        translate(Vector3f(12f, 300f, 2f))
        rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), 1f, 0f, 0f)), 0f, 0f, 0f)
    },
    val camera: CameraComponent = CameraComponent().apply {
        width = 1500f
        height = 1500f
        far = (-5000).toFloat()
        perspective = false
    },
    var color: Vector3f = Vector3f(1f, 1f, 1f),
    var scatterFactor: Float = 1f,
): Component()