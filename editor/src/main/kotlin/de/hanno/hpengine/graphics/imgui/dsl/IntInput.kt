package de.hanno.hpengine.engine.graphics.imgui

import imgui.ImGui

fun intInput(
    label: String,
    initial: Int,
    min: Int = 1,
    max: Int = 100,
    onChange: (IntArray) -> Unit
) {
    val intArray = intArrayOf(initial)
    if (ImGui.sliderInt(label, intArray, min, max)) {
        onChange(intArray)
    }
}
