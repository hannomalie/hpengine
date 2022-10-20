package de.hanno.hpengine.artemis

import com.artemis.Component
import org.joml.Vector3f

class DirectionalLightComponent(
    var color: Vector3f = Vector3f(1f, 1f, 1f),
    var scatterFactor: Float = 1f,
): Component()