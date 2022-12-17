package de.hanno.hpengine.graphics.shader

class ShaderLoadException(private val shaderSource: String) : RuntimeException() {
    override fun toString(): String =
        shaderSource.lines().mapIndexed { index, it -> "${index + 1}:$it\n" }.fold("") { a, b -> a + b }
}