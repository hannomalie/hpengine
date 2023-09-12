package de.hanno.hpengine.input

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.window.Window
import org.joml.Vector2i
import org.koin.core.annotation.Single

import org.lwjgl.glfw.GLFW.*

@Single
class Input(
    private val window: Window
) {
    private val keysPressed = IntArrayList()
    private val keysPressedLastFrame = IntArrayList()
    private val keysReleased = IntArrayList()

    private val mousePressed = IntArrayList()
    private val mouseReleased = IntArrayList()

    private val mousePressedLastFrame = IntArrayList()

    private val FIRST_KEY = GLFW_KEY_SPACE
    private val NUM_KEYS = GLFW_KEY_LAST - GLFW_KEY_SPACE
    private val NUM_BUTTONS = 3

    var dx: Int = 0
        private set
    var dy: Int = 0
        private set

    private var dxLast: Int = 0
    private var dyLast: Int = 0

    private var dxBeforeLast: Int = 0
    private var dyBeforeLast: Int = 0

    private val mouseX = DoubleArray(1)
    private val mouseY = DoubleArray(1)
    private val mouseXLast = DoubleArray(1)
    private val mouseYLast = DoubleArray(1)
    private val width = IntArray(1)
    private val height = IntArray(1)

    val dxSmooth: Int get() = (dx + dxLast + dxBeforeLast) / 3

    val dySmooth: Int get() = (dy + dyLast + dyBeforeLast) / 3

    fun update() {
        updateKeyboard()
        updateMouse()
    }

    private fun updateKeyboard() {
        keysPressedLastFrame.clear()
        keysPressedLastFrame.addAll(keysPressed)
        keysPressed.clear()
        keysReleased.clear()

        for (i in FIRST_KEY until NUM_KEYS) {
            if (isKeyPressedImpl(window, i)) {
                keysPressed.add(i)
            } else if (isKeyReleasedImpl(window, i)) {
                keysReleased.add(i)
            }
        }
    }

    private fun updateMouse() {
        mousePressedLastFrame.clear()
        mousePressedLastFrame.addAll(mousePressed)

        mousePressed.clear()
        mouseReleased.clear()

        for (i in 0 until NUM_BUTTONS) {
            if (isMousePressedImpl(i)) {
                mousePressed.add(i)
            }
        }

        for (i in 0 until NUM_BUTTONS) {
            if (isMouseReleasedImpl(i)) {
                mouseReleased.add(i)
            }
        }

        dxBeforeLast = dxLast
        dyBeforeLast = dyLast
        dxLast = dx
        dyLast = dy
        mouseXLast[0] = mouseX[0]
        mouseYLast[0] = mouseY[0]
        window.getCursorPosition(mouseX, mouseY)
        window.getFrameBufferSize(width, height)
        dx = (-(mouseXLast[0] - mouseX[0])).toInt()
        dy = (mouseYLast[0] - mouseY[0]).toInt()
    }

    private fun isKeyPressedImpl(window: Window, keyCode: Int): Boolean {
        val action = window.getKey(keyCode)
        return action == GLFW_PRESS || action == GLFW_REPEAT
    }
    private fun isKeyReleasedImpl(window: Window, keyCode: Int) = window.getKey(keyCode) == GLFW_RELEASE
    private fun isMousePressedImpl(buttonCode: Int) = window.getMouseButton(buttonCode) == GLFW_PRESS
    private fun isMouseReleasedImpl(buttonCode: Int) = window.getMouseButton(buttonCode) == GLFW_RELEASE

    fun isKeyPressed(keyCode: Int) = keysPressed.contains(keyCode)
    fun isKeyReleased(keyCode: Int) = keysReleased.contains(keyCode)
    fun wasKeyClicked(keyCode: Int) = wasKeyPressedLastFrame(keyCode) && isKeyReleased(keyCode)

    fun wasKeyPressedLastFrame(keyCode: Int) = keysPressedLastFrame.contains(keyCode)
    fun wasKeyReleasedLastFrame(keyCode: Int) = !wasKeyPressedLastFrame(keyCode)

    fun isMousePressed(buttonCode: Int) = mousePressed.contains(buttonCode)
    fun isMouseClicked(buttonCode: Int): Boolean = mousePressedLastFrame.contains(buttonCode) && isMouseReleased(buttonCode)
    fun isMouseReleased(buttonCode: Int) = mouseReleased.contains(buttonCode)

    fun getMouseX() = mouseX[0].toInt()
    fun getMouseY() = height[0] - mouseY[0].toInt()
    fun getMouseXY() = Vector2i(getMouseX(), getMouseY())

}
