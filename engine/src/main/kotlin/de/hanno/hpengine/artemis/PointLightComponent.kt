package de.hanno.hpengine.artemis

import com.artemis.Component
import org.joml.Vector4f

class PointLightComponent: Component() {
    val color: Vector4f = Vector4f(1f)
    var radius: Float = 50f
    var renderedSphereRadius: Float = 0f
}