package de.hanno.hpengine.ocean

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.graphics.imgui.float2Input
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import imgui.ImGui
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.annotation.Single
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


data class OceanWaterSelection(val oceanWater: OceanWaterComponent): Selection

@Single(binds = [EditorExtension::class])
class OceanWaterEditorExtension: EditorExtension {
    override fun getSelectionForComponentOrNull(
        component: Component,
        entity: Int,
        components: Bag<Component>,
    ) = if(component is OceanWaterComponent) {
        OceanWaterSelection(component)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is OceanWaterSelection) {
        oceanWaterInputs(selection)
        true
    } else {
        false
    }
}

private fun Window.oceanWaterInputs(selection: OceanWaterSelection) {
    val oceanWater = selection.oceanWater
    val colors = floatArrayOf(
        oceanWater.albedo.x,
        oceanWater.albedo.y,
        oceanWater.albedo.z
    )
    if (ImGui.colorPicker3("Albedo", colors)) {
        oceanWater.albedo.x = colors[0]
        oceanWater.albedo.y = colors[1]
        oceanWater.albedo.z = colors[2]
    }
    floatInput("Amplitude", oceanWater.amplitude, min = 0.1f, max = 5f) { floatArray ->
        oceanWater.amplitude = floatArray[0]
    }
    floatInput("Windspeed", oceanWater.windspeed, min = 0.0f, max = 250f) { floatArray ->
        oceanWater.windspeed = floatArray[0]
    }
    float2Input(
        "Direction",
        oceanWater.direction.x,
        oceanWater.direction.y,
        min = 0.0f,
        max = 1.0f
    ) { floatArray ->
        oceanWater.direction.x = floatArray[0]
        oceanWater.direction.y = floatArray[1]
    }
    floatInput(
        "Wave Height",
        oceanWater.waveHeight,
        min = 0.0f,
        max = 10.0f
    ) { floatArray ->
        oceanWater.waveHeight = floatArray[0]
    }
    checkBox("Choppy", oceanWater.choppy) { boolean ->
        oceanWater.choppy = boolean
    }
    floatInput("Choppiness", oceanWater.choppiness, min = 0.0f, max = 1.0f) { floatArray ->
        oceanWater.choppiness = floatArray[0]
    }
    floatInput("Time Factor", oceanWater.timeFactor, min = 0.0f, max = 10f) { floatArray ->
        oceanWater.timeFactor = floatArray[0]
    }
    if (ImGui.button("Init new randomness")) {
        oceanWater.initRandomNess = true
    }
}
