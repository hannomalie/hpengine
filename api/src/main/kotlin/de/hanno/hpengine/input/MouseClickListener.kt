package de.hanno.hpengine.input

import org.joml.Vector2i
import org.lwjgl.glfw.GLFW

class MouseClickListener(private val input: Input) {
    var clicked = false
    private var mousePressStartPosition: Vector2i? = null

    fun update(deltaSeconds: Float) {
        if(mousePressStartPosition == null && input.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            mousePressStartPosition = Vector2i(input.getMouseXY())
        } else if(input.isMouseReleased(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            mousePressStartPosition?.let { mousePressStartPosition ->
                val distance = input.getMouseXY().distance(mousePressStartPosition)
                if(distance <= 1.0) {
                    clicked = true
                }
                this.mousePressStartPosition = null
            }
        }
    }

    inline fun <T> consumeClick(onClick: () -> T): T? = if(clicked) {
        try {
            onClick()
        } finally {
            clicked = false
        }
    } else null
}