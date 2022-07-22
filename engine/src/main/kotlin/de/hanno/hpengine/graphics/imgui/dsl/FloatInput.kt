package de.hanno.hpengine.engine.graphics.imgui

import imgui.ImGui

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