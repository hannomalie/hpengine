package de.hanno.hpengine.engine.graphics.imgui

import imgui.ImGui
import org.joml.Vector3f
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

fun floatInput(
    label: String,
    initial: Float,
    min: Float = 0.001f,
    max: Float = 1.0f,
    onChange: (FloatArray) -> Unit
) {
    val floatArray = floatArrayOf(initial)
    if (ImGui.sliderFloat(label, floatArray, min, max)) {
        onChange(floatArray)
    }
}
fun floatInput(
    label: String,
    property: KMutableProperty0<Float>,
    min: Float = 0.001f,
    max: Float = 1.0f,
) {
    val floatArray = floatArrayOf(property.get())
    if (ImGui.sliderFloat(label, floatArray, min, max)) {
        property.set(floatArray.first())
    }
}

fun float2Input(
    label: String,
    initial0: Float,
    initial1: Float,
    min: Float = 0.001f,
    max: Float = 1.0f,
    onChange: (FloatArray) -> Unit
) {
    val floatArray = floatArrayOf(initial0, initial1)
    if (ImGui.sliderFloat2(label, floatArray, min, max)) {
        onChange(floatArray)
    }
}
fun vector3fInput(
    label: String,
    property: KProperty0<Vector3f>,
    min: Float = 0.001f,
    max: Float = 1.0f,
) {
    val floatArray = floatArrayOf(property.get().x, property.get().y, property.get().z)
    if (ImGui.sliderFloat3(label, floatArray, min, max)) {
        property.get().set(floatArray[0], floatArray[1], floatArray[2])
    }
}