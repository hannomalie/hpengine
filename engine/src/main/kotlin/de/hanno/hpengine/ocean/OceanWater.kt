package de.hanno.hpengine.ocean

import com.artemis.Component
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.pow

class OceanWaterComponent: Component() {
    var amplitude: Float = 2f
    var windspeed: Float = recommendedIntensity
    var timeFactor: Float = 1f
    var direction: Vector2f = Vector2f(0.25f, 1.0f)
    var albedo: Vector3f = Vector3f(0f, 0.1f, 1f)
    var waveHeight = 1f
    var choppiness = 1f
    var initRandomNess = false

    val L: Int get() = (windspeed.pow(2.0f) /9.81f).toInt()

    var choppy: Boolean
        get() = choppiness != 0f
        set(value) {
            choppiness = if(value) 1f else 0f
        }

    companion object {
        val recommendedIntensity = 26f
    }
}
