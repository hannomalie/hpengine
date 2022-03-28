package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import org.joml.Vector3f

class TubeLightComponent: Component() {
    var color: Vector3f = Vector3f(1f)
    var radius = 2f
    var length = 10f
}