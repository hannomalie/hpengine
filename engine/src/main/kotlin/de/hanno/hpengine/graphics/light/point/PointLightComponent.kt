package de.hanno.hpengine.graphics.light.point

import com.artemis.Component
import org.joml.Vector4f

class PointLightComponent: Component() {
    val color: Vector4f = Vector4f(1f)
    var radius: Float = 100f
    var shadow: Boolean = true
}