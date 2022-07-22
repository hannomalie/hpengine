package de.hanno.hpengine.artemis

import com.artemis.Component
import org.joml.Vector3f

class DirectionalLightComponent(
    val camera: CameraComponent = CameraComponent().apply {
        width = 1500f
        height = 1500f
        far = (-5000).toFloat()
        perspective = false
    },
    var color: Vector3f = Vector3f(1f, 1f, 1f),
    var scatterFactor: Float = 1f,
): Component()