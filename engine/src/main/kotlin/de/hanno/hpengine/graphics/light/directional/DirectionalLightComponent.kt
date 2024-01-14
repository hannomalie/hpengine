package de.hanno.hpengine.graphics.light.directional

import com.artemis.Component
import com.sksamuel.hoplite.fp.invalid
import org.joml.Vector3f
import org.joml.Vector3fc
import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.sin

class DirectionalLightComponent(
    var color: Vector3f = Vector3f(1f, 1f, 1f),
    var scatterFactor: Float = 1f,
    phiDegrees: Float = 24.0f,
    thetaDegrees: Float = 15.0f,
    var height: Float = 1000f,
): Component() {
    val _direction: Vector3f = Vector3f(0.2f, -1f, 0.0f)
    val direction: Vector3fc get() = _direction

    var phiDegrees: Float = phiDegrees
        set(value) {
            field = value
            calculateDirection()
        }
    var thetaDegrees: Float = thetaDegrees
        set(value) {
            field = value
            calculateDirection()
        }

    init {
        calculateDirection()
    }

    private fun calculateDirection() {
        val translation = Vector3f(
            sin(toRadians(phiDegrees.toDouble()).toFloat()) * cos(toRadians(thetaDegrees.toDouble())).toFloat(),
            cos(toRadians(phiDegrees.toDouble()).toFloat()),
            sin(toRadians(phiDegrees.toDouble()).toFloat()) * sin(toRadians(thetaDegrees.toDouble())).toFloat()
        )
        _direction.set(0f).sub(translation).normalize()
    }
}
