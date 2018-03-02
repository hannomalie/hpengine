package de.hanno.hpengine.engine.input

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.ClickEvent
import de.hanno.hpengine.engine.graphics.GpuContext

import org.lwjgl.glfw.GLFW.*

class Input(val engine: Engine, private val gpuContext: GpuContext) {

    private val currentKeys = IntArrayList()
    private val keysPressed = IntArrayList()
    private val keysReleased = IntArrayList()

    private val currentMouse = IntArrayList()
    private val downMouse = IntArrayList()
    private val upMouse = IntArrayList()

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

    private val HOLD_KEYS_AS_PRESSED_KEYS = true

    private var MOUSE_LEFT_PRESSED_LAST_FRAME: Boolean = false
    private var STRG_PRESSED_LAST_FRAME = false
    @Volatile
    var pickingClick = 0
    private val mouseX = DoubleArray(1)
    private val mouseY = DoubleArray(1)
    private val mouseXLast = DoubleArray(1)
    private val mouseYLast = DoubleArray(1)

    val dxSmooth: Int
        get() = (dx + dxLast + dxBeforeLast) / 3

    val dySmooth: Int
        get() = (dy + dyLast + dyBeforeLast) / 3

    fun update() {
        updateKeyboard()
        updateMouse()
    }

    private fun updateKeyboard() {

        if (isMouseClicked(0)) {
            if (!MOUSE_LEFT_PRESSED_LAST_FRAME) {
                engine.eventBus.post(ClickEvent())
            }
            MOUSE_LEFT_PRESSED_LAST_FRAME = true
        } else {
            MOUSE_LEFT_PRESSED_LAST_FRAME = false
        }
        run {
            if (pickingClick == 0 && isKeyDown(gpuContext, GLFW_KEY_LEFT_CONTROL)) {
                if (isMouseClicked(0) && !STRG_PRESSED_LAST_FRAME) {
                    pickingClick = 1
                    STRG_PRESSED_LAST_FRAME = true
                }
            } else {
                STRG_PRESSED_LAST_FRAME = false
            }
        }

        keysPressed.clear()
        keysReleased.clear()

        if (HOLD_KEYS_AS_PRESSED_KEYS) {
            currentKeys.clear()
        }

        for (i in FIRST_KEY until NUM_KEYS) {
            if (isKeyDown(gpuContext, i) && !currentKeys.contains(i)) {
                keysPressed.add(i)
            }
        }

        for (i in FIRST_KEY until NUM_KEYS) {
            if (!isKeyDown(gpuContext, i) && currentKeys.contains(i)) {
                keysReleased.add(i)
            }
        }

        currentKeys.clear()

        for (i in 0 until NUM_KEYS) {
            if (isKeyDown(gpuContext, FIRST_KEY + i)) {
                currentKeys.add(i)
            }
        }
    }

    private fun updateMouse() {
        downMouse.clear()
        upMouse.clear()

        if (HOLD_KEYS_AS_PRESSED_KEYS) {
            currentMouse.clear()
        }

        for (i in 0 until NUM_BUTTONS) {
            if (isMouseDown(i) && !currentMouse.contains(i)) {
                downMouse.add(i)
            }
        }

        for (i in 0 until NUM_BUTTONS) {
            if (!isMouseDown(i) && currentMouse.contains(i)) {
                upMouse.add(i)
            }
        }

        currentMouse.clear()

        for (i in 0 until NUM_BUTTONS) {
            if (isMouseDown(i)) {
                currentMouse.add(i)
            }
        }

        dxBeforeLast = dxLast
        dyBeforeLast = dyLast
        dxLast = dx
        dyLast = dy
        mouseXLast[0] = mouseX[0]
        mouseYLast[0] = mouseY[0]
        glfwGetCursorPos(gpuContext.windowHandle, mouseX, mouseY)
        dx = (-(mouseXLast[0] - mouseX[0])).toInt()
        dy = (mouseYLast[0] - mouseY[0]).toInt()
    }


    private fun isKeyDown(gpuContext: GpuContext, keyCode: Int): Boolean {
        return glfwGetKey(gpuContext.windowHandle, keyCode) == GLFW_PRESS
    }

    fun isKeyPressed(keyCode: Int): Boolean {
        return keysPressed.contains(keyCode)
    }

    fun isKeyUp(keyCode: Int): Boolean {
        return keysReleased.contains(keyCode)
    }


    private fun isMouseDown(buttonCode: Int): Boolean {
        return glfwGetMouseButton(gpuContext.windowHandle, buttonCode) == GLFW_PRESS
    }

    fun isMouseClicked(buttonCode: Int): Boolean {
        return downMouse.contains(buttonCode)
    }

    fun isMouseUp(buttonCode: Int): Boolean {
        return upMouse.contains(buttonCode)
    }

    fun getMouseX(): Int {
        return mouseX[0].toInt()
    }

    fun getMouseY(): Int {
        return Config.getInstance().height - mouseY[0].toInt()
    }

}
