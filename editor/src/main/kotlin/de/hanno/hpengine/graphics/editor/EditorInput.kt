package de.hanno.hpengine.graphics.editor

import com.artemis.BaseSystem
import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import org.joml.Vector2i
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW

@Single(binds = [Input::class, BaseSystem::class, EditorInput::class])
class EditorInput(
    private val window: Window
): BaseSystem(), Input {

    internal var swallowInput = false
    internal var prioritizeGameInput = false

    override val keysPressed = IntArrayList()
    override val keysPressedLastFrame = IntArrayList()
    override val keysReleased = IntArrayList()

    override val mousePressed = IntArrayList()
    override val mouseReleased = IntArrayList()

    override val mousePressedLastFrame = IntArrayList()

    override val FIRST_KEY = GLFW.GLFW_KEY_SPACE
    override val NUM_KEYS = GLFW.GLFW_KEY_LAST
    override val NUM_BUTTONS = 3

    override var dx: Int = 0
        private set
    override var dy: Int = 0
        private set

    override var dxLast: Int = 0
    override var dyLast: Int = 0

    override var dxBeforeLast: Int = 0
    override var dyBeforeLast: Int = 0

    override val mouseX = DoubleArray(1)
    override val mouseY = DoubleArray(1)
    override val mouseXLast = DoubleArray(1)
    override val mouseYLast = DoubleArray(1)
    override val width = IntArray(1)
    override val height = IntArray(1)

    override val dxSmooth: Int get() = (dx + dxLast + dxBeforeLast) / 3

    override val dySmooth: Int get() = (dy + dyLast + dyBeforeLast) / 3

    override fun update() {
        val escPressed = wasKeyReleasedLastFrame(GLFW.GLFW_KEY_ESCAPE) && isKeyPressed(GLFW.GLFW_KEY_ESCAPE)

        if(escPressed) {
            prioritizeGameInput = !prioritizeGameInput
        }
        if (swallowInput && !prioritizeGameInput) {
            clear()
        } else {
            updateKeyboard()
            updateMouse()
        }
    }

    override fun updateKeyboard() {
        keysPressedLastFrame.clear()
        keysPressedLastFrame.addAll(keysPressed)
        clearKeys()

        for (i in FIRST_KEY until NUM_KEYS) {
            if (isKeyPressedImpl(window, i)) {
                keysPressed.add(i)
            } else if (isKeyReleasedImpl(window, i)) {
                keysReleased.add(i)
            }
        }
    }

    override fun updateMouse() {
        mousePressedLastFrame.clear()
        mousePressedLastFrame.addAll(mousePressed)

        clearMouse()

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

    override fun isKeyPressedImpl(window: Window, keyCode: Int): Boolean {
        val action = window.getKey(keyCode)
        return action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT
    }
    override fun isKeyReleasedImpl(window: Window, keyCode: Int) = window.getKey(keyCode) == GLFW.GLFW_RELEASE
    override fun isMousePressedImpl(buttonCode: Int) = window.getMouseButton(buttonCode) == GLFW.GLFW_PRESS
    override fun isMouseReleasedImpl(buttonCode: Int) = window.getMouseButton(buttonCode) == GLFW.GLFW_RELEASE

    override fun isKeyPressed(keyCode: Int) = keysPressed.contains(keyCode)
    override fun isKeyReleased(keyCode: Int) = keysReleased.contains(keyCode)
    override fun wasKeyClicked(keyCode: Int) = wasKeyPressedLastFrame(keyCode) && isKeyReleased(keyCode)

    override fun wasKeyPressedLastFrame(keyCode: Int) = keysPressedLastFrame.contains(keyCode)
    override fun wasKeyReleasedLastFrame(keyCode: Int) = !wasKeyPressedLastFrame(keyCode)

    override fun isMousePressed(buttonCode: Int) = mousePressed.contains(buttonCode)
    override fun isMouseClicked(buttonCode: Int): Boolean = mousePressedLastFrame.contains(buttonCode) && isMouseReleased(buttonCode)
    override fun isMouseReleased(buttonCode: Int) = mouseReleased.contains(buttonCode)

    override fun getMouseX() = mouseX[0].toInt()
    override fun getMouseY() = height[0] - mouseY[0].toInt()
    override fun getMouseXY() = Vector2i(getMouseX(), getMouseY())

    override fun processSystem() { }

    override fun clearKeys() {
        keysPressed.clear()
        keysReleased.clear()
    }

    override fun clearMouse() {
        mousePressed.clear()
        mouseReleased.clear()
        mousePressedLastFrame.clear()
        dx = 0
        dy = 0
        dxLast = 0
        dyLast = 0
        dxBeforeLast = 0
        dyBeforeLast = 0
    }

    override fun clear() {
        clearMouse()
        clearKeys()
    }
}