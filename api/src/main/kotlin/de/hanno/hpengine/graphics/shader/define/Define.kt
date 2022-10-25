package de.hanno.hpengine.graphics.shader.define

data class Define<T> constructor(val name: String, val backingValue: T) {
    val defineString = "#define $name $backingValue\n"

    init {
        require(name.isNotEmpty()) { "No empty name for define!" }
    }
}