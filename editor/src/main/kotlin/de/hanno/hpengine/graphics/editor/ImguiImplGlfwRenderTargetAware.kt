package imgui.glfw

import imgui.ImGui
import imgui.ImGuiViewport
import imgui.ImVec2
import imgui.callback.*
import imgui.flag.*
import imgui.glfw.ImGuiImplGlfw.MapAnalog
import imgui.glfw.ImGuiImplGlfw.MapButton
import imgui.lwjgl3.glfw.ImGuiImplGlfwNative
import org.lwjgl.glfw.*
import org.lwjgl.system.Callback
import org.lwjgl.system.MemoryUtil
import java.util.*

/**
 * This class is a straightforward port of the
 * [imgui_impl_glfw.cpp](https://raw.githubusercontent.com/ocornut/imgui/1ee252772ae9c0a971d06257bb5c89f628fa696a/backends/imgui_impl_glfw.cpp).
 *
 *
 * It supports clipboard, gamepad, mouse and keyboard in the same way the original Dear ImGui code does. You can copy-paste this class in your codebase and
 * modify the rendering routine in the way you'd like.
 */
// This file is forked from https://github.com/SpaiR/imgui-java/blob/main/imgui-lwjgl3/src/main/java/imgui/glfw/ImGuiImplGlfw.java
// to support adapting to framebuffer in the newFrame method
class ImGuiImplGlfw {
    /**
     * Data class to store implementation specific fields.
     * Same as `ImGui_ImplGlfw_Data`.
     */
    protected class Data() {
        var window: Long = -1
        var time: Double = 0.0
        var mouseWindow: Long = -1
        var mouseCursors: LongArray = LongArray(ImGuiMouseCursor.COUNT)
        var lastValidMousePos: ImVec2 = ImVec2()
        var keyOwnerWindows: LongArray = LongArray(GLFW.GLFW_KEY_LAST)
        var installedCallbacks: Boolean = false
        var callbacksChainForAllWindows: Boolean = false
        var wantUpdateMonitors: Boolean = true

        // Chain GLFW callbacks: our callbacks will call the user's previously installed callbacks, if any.
        var prevUserCallbackWindowFocus: GLFWWindowFocusCallback? = null
        var prevUserCallbackCursorPos: GLFWCursorPosCallback? = null
        var prevUserCallbackCursorEnter: GLFWCursorEnterCallback? = null
        var prevUserCallbackMousebutton: GLFWMouseButtonCallback? = null
        var prevUserCallbackScroll: GLFWScrollCallback? = null
        var prevUserCallbackKey: GLFWKeyCallback? = null
        var prevUserCallbackChar: GLFWCharCallback? = null
        var prevUserCallbackMonitor: GLFWMonitorCallback? =
            null // This field is required to use GLFW with touch screens on Windows.
        // For compatibility reasons it was added here as a comment. But we don't use somewhere in the binding implementation.
        // protected long glfwWndProc;
    }

    /**
     * Internal class to store containers for frequently used arrays.
     * This class helps minimize the number of object allocations on the JVM side,
     * thereby improving performance and reducing garbage collection overhead.
     */
    private class Properties() {
        val windowW: IntArray = IntArray(1)
        val windowH: IntArray = IntArray(1)
        val windowX: IntArray = IntArray(1)
        val windowY: IntArray = IntArray(1)
        val displayW: IntArray = IntArray(1)
        val displayH: IntArray = IntArray(1)

        // For mouse tracking
        val mousePosPrev: ImVec2 = ImVec2()
        val mouseX: DoubleArray = DoubleArray(1)
        val mouseY: DoubleArray = DoubleArray(1)

        // Monitor properties
        val monitorX: IntArray = IntArray(1)
        val monitorY: IntArray = IntArray(1)
        val monitorWorkAreaX: IntArray = IntArray(1)
        val monitorWorkAreaY: IntArray = IntArray(1)
        val monitorWorkAreaWidth: IntArray = IntArray(1)
        val monitorWorkAreaHeight: IntArray = IntArray(1)
        val monitorContentScaleX: FloatArray = FloatArray(1)
        val monitorContentScaleY: FloatArray = FloatArray(1)

        // For char translation
        val charNames: String = "`-=[]\\,;'./"
        val charKeys: IntArray = intArrayOf(
            GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_LEFT_BRACKET,
            GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_SEMICOLON,
            GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH
        )
    }

    protected var data: Data? = null
    private val props = Properties()

    protected val clipboardTextFn: ImStrSupplier
        get() = object : ImStrSupplier() {
            override fun get(): String {
                val clipboardString: String? = GLFW.glfwGetClipboardString(data!!.window)
                return if (clipboardString != null) clipboardString else ""
            }
        }

    protected fun setClipboardTextFn(): ImStrConsumer {
        return object : ImStrConsumer() {
            override fun accept(text: String) {
                GLFW.glfwSetClipboardString(data!!.window, text)
            }
        }
    }

    protected fun glfwKeyToImGuiKey(glfwKey: Int): Int {
        when (glfwKey) {
            GLFW.GLFW_KEY_TAB -> return ImGuiKey.Tab
            GLFW.GLFW_KEY_LEFT -> return ImGuiKey.LeftArrow
            GLFW.GLFW_KEY_RIGHT -> return ImGuiKey.RightArrow
            GLFW.GLFW_KEY_UP -> return ImGuiKey.UpArrow
            GLFW.GLFW_KEY_DOWN -> return ImGuiKey.DownArrow
            GLFW.GLFW_KEY_PAGE_UP -> return ImGuiKey.PageUp
            GLFW.GLFW_KEY_PAGE_DOWN -> return ImGuiKey.PageDown
            GLFW.GLFW_KEY_HOME -> return ImGuiKey.Home
            GLFW.GLFW_KEY_END -> return ImGuiKey.End
            GLFW.GLFW_KEY_INSERT -> return ImGuiKey.Insert
            GLFW.GLFW_KEY_DELETE -> return ImGuiKey.Delete
            GLFW.GLFW_KEY_BACKSPACE -> return ImGuiKey.Backspace
            GLFW.GLFW_KEY_SPACE -> return ImGuiKey.Space
            GLFW.GLFW_KEY_ENTER -> return ImGuiKey.Enter
            GLFW.GLFW_KEY_ESCAPE -> return ImGuiKey.Escape
            GLFW.GLFW_KEY_APOSTROPHE -> return ImGuiKey.Apostrophe
            GLFW.GLFW_KEY_COMMA -> return ImGuiKey.Comma
            GLFW.GLFW_KEY_MINUS -> return ImGuiKey.Minus
            GLFW.GLFW_KEY_PERIOD -> return ImGuiKey.Period
            GLFW.GLFW_KEY_SLASH -> return ImGuiKey.Slash
            GLFW.GLFW_KEY_SEMICOLON -> return ImGuiKey.Semicolon
            GLFW.GLFW_KEY_EQUAL -> return ImGuiKey.Equal
            GLFW.GLFW_KEY_LEFT_BRACKET -> return ImGuiKey.LeftBracket
            GLFW.GLFW_KEY_BACKSLASH -> return ImGuiKey.Backslash
            GLFW.GLFW_KEY_RIGHT_BRACKET -> return ImGuiKey.RightBracket
            GLFW.GLFW_KEY_GRAVE_ACCENT -> return ImGuiKey.GraveAccent
            GLFW.GLFW_KEY_CAPS_LOCK -> return ImGuiKey.CapsLock
            GLFW.GLFW_KEY_SCROLL_LOCK -> return ImGuiKey.ScrollLock
            GLFW.GLFW_KEY_NUM_LOCK -> return ImGuiKey.NumLock
            GLFW.GLFW_KEY_PRINT_SCREEN -> return ImGuiKey.PrintScreen
            GLFW.GLFW_KEY_PAUSE -> return ImGuiKey.Pause
            GLFW.GLFW_KEY_KP_0 -> return ImGuiKey.Keypad0
            GLFW.GLFW_KEY_KP_1 -> return ImGuiKey.Keypad1
            GLFW.GLFW_KEY_KP_2 -> return ImGuiKey.Keypad2
            GLFW.GLFW_KEY_KP_3 -> return ImGuiKey.Keypad3
            GLFW.GLFW_KEY_KP_4 -> return ImGuiKey.Keypad4
            GLFW.GLFW_KEY_KP_5 -> return ImGuiKey.Keypad5
            GLFW.GLFW_KEY_KP_6 -> return ImGuiKey.Keypad6
            GLFW.GLFW_KEY_KP_7 -> return ImGuiKey.Keypad7
            GLFW.GLFW_KEY_KP_8 -> return ImGuiKey.Keypad8
            GLFW.GLFW_KEY_KP_9 -> return ImGuiKey.Keypad9
            GLFW.GLFW_KEY_KP_DECIMAL -> return ImGuiKey.KeypadDecimal
            GLFW.GLFW_KEY_KP_DIVIDE -> return ImGuiKey.KeypadDivide
            GLFW.GLFW_KEY_KP_MULTIPLY -> return ImGuiKey.KeypadMultiply
            GLFW.GLFW_KEY_KP_SUBTRACT -> return ImGuiKey.KeypadSubtract
            GLFW.GLFW_KEY_KP_ADD -> return ImGuiKey.KeypadAdd
            GLFW.GLFW_KEY_KP_ENTER -> return ImGuiKey.KeypadEnter
            GLFW.GLFW_KEY_KP_EQUAL -> return ImGuiKey.KeypadEqual
            GLFW.GLFW_KEY_LEFT_SHIFT -> return ImGuiKey.LeftShift
            GLFW.GLFW_KEY_LEFT_CONTROL -> return ImGuiKey.LeftCtrl
            GLFW.GLFW_KEY_LEFT_ALT -> return ImGuiKey.LeftAlt
            GLFW.GLFW_KEY_LEFT_SUPER -> return ImGuiKey.LeftSuper
            GLFW.GLFW_KEY_RIGHT_SHIFT -> return ImGuiKey.RightShift
            GLFW.GLFW_KEY_RIGHT_CONTROL -> return ImGuiKey.RightCtrl
            GLFW.GLFW_KEY_RIGHT_ALT -> return ImGuiKey.RightAlt
            GLFW.GLFW_KEY_RIGHT_SUPER -> return ImGuiKey.RightSuper
            GLFW.GLFW_KEY_MENU -> return ImGuiKey.Menu
            GLFW.GLFW_KEY_0 -> return ImGuiKey._0
            GLFW.GLFW_KEY_1 -> return ImGuiKey._1
            GLFW.GLFW_KEY_2 -> return ImGuiKey._2
            GLFW.GLFW_KEY_3 -> return ImGuiKey._3
            GLFW.GLFW_KEY_4 -> return ImGuiKey._4
            GLFW.GLFW_KEY_5 -> return ImGuiKey._5
            GLFW.GLFW_KEY_6 -> return ImGuiKey._6
            GLFW.GLFW_KEY_7 -> return ImGuiKey._7
            GLFW.GLFW_KEY_8 -> return ImGuiKey._8
            GLFW.GLFW_KEY_9 -> return ImGuiKey._9
            GLFW.GLFW_KEY_A -> return ImGuiKey.A
            GLFW.GLFW_KEY_B -> return ImGuiKey.B
            GLFW.GLFW_KEY_C -> return ImGuiKey.C
            GLFW.GLFW_KEY_D -> return ImGuiKey.D
            GLFW.GLFW_KEY_E -> return ImGuiKey.E
            GLFW.GLFW_KEY_F -> return ImGuiKey.F
            GLFW.GLFW_KEY_G -> return ImGuiKey.G
            GLFW.GLFW_KEY_H -> return ImGuiKey.H
            GLFW.GLFW_KEY_I -> return ImGuiKey.I
            GLFW.GLFW_KEY_J -> return ImGuiKey.J
            GLFW.GLFW_KEY_K -> return ImGuiKey.K
            GLFW.GLFW_KEY_L -> return ImGuiKey.L
            GLFW.GLFW_KEY_M -> return ImGuiKey.M
            GLFW.GLFW_KEY_N -> return ImGuiKey.N
            GLFW.GLFW_KEY_O -> return ImGuiKey.O
            GLFW.GLFW_KEY_P -> return ImGuiKey.P
            GLFW.GLFW_KEY_Q -> return ImGuiKey.Q
            GLFW.GLFW_KEY_R -> return ImGuiKey.R
            GLFW.GLFW_KEY_S -> return ImGuiKey.S
            GLFW.GLFW_KEY_T -> return ImGuiKey.T
            GLFW.GLFW_KEY_U -> return ImGuiKey.U
            GLFW.GLFW_KEY_V -> return ImGuiKey.V
            GLFW.GLFW_KEY_W -> return ImGuiKey.W
            GLFW.GLFW_KEY_X -> return ImGuiKey.X
            GLFW.GLFW_KEY_Y -> return ImGuiKey.Y
            GLFW.GLFW_KEY_Z -> return ImGuiKey.Z
            GLFW.GLFW_KEY_F1 -> return ImGuiKey.F1
            GLFW.GLFW_KEY_F2 -> return ImGuiKey.F2
            GLFW.GLFW_KEY_F3 -> return ImGuiKey.F3
            GLFW.GLFW_KEY_F4 -> return ImGuiKey.F4
            GLFW.GLFW_KEY_F5 -> return ImGuiKey.F5
            GLFW.GLFW_KEY_F6 -> return ImGuiKey.F6
            GLFW.GLFW_KEY_F7 -> return ImGuiKey.F7
            GLFW.GLFW_KEY_F8 -> return ImGuiKey.F8
            GLFW.GLFW_KEY_F9 -> return ImGuiKey.F9
            GLFW.GLFW_KEY_F10 -> return ImGuiKey.F10
            GLFW.GLFW_KEY_F11 -> return ImGuiKey.F11
            GLFW.GLFW_KEY_F12 -> return ImGuiKey.F12
            else -> return ImGuiKey.None
        }
    }

    // X11 does not include current pressed/released modifier key in 'mods' flags submitted by GLFW
    // See https://github.com/ocornut/imgui/issues/6034 and https://github.com/glfw/glfw/issues/1630
    protected fun updateKeyModifiers(window: Long) {
        val io = ImGui.getIO()
        io.addKeyEvent(
            ImGuiKey.ModCtrl,
            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_CONTROL
            ) == GLFW.GLFW_PRESS)
        )
        io.addKeyEvent(
            ImGuiKey.ModShift,
            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_SHIFT
            ) == GLFW.GLFW_PRESS)
        )
        io.addKeyEvent(
            ImGuiKey.ModAlt,
            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_ALT
            ) == GLFW.GLFW_PRESS)
        )
        io.addKeyEvent(
            ImGuiKey.ModSuper,
            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_SUPER
            ) == GLFW.GLFW_PRESS)
        )
    }

    protected fun shouldChainCallback(window: Long): Boolean {
        return if (data!!.callbacksChainForAllWindows) true else (window == data!!.window)
    }

    fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (data!!.prevUserCallbackMousebutton != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackMousebutton!!.invoke(window, button, action, mods)
        }

        updateKeyModifiers(window)

        val io = ImGui.getIO()
        if (button >= 0 && button < ImGuiMouseButton.COUNT) {
            io.addMouseButtonEvent(button, action == GLFW.GLFW_PRESS)
        }
    }

    fun scrollCallback(window: Long, xOffset: Double, yOffset: Double) {
        if (data!!.prevUserCallbackScroll != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackScroll!!.invoke(window, xOffset, yOffset)
        }

        val io = ImGui.getIO()
        io.addMouseWheelEvent(xOffset.toFloat(), yOffset.toFloat())
    }

    protected fun translateUntranslatedKey(key: Int, scancode: Int): Int {
        if (!glfwHasGetKeyName) {
            return key
        }

        // GLFW 3.1+ attempts to "untranslate" keys, which goes the opposite of what every other framework does, making using lettered shortcuts difficult.
        // (It had reasons to do so: namely GLFW is/was more likely to be used for WASD-type game controls rather than lettered shortcuts, but IHMO the 3.1 change could have been done differently)
        // See https://github.com/glfw/glfw/issues/1502 for details.
        // Adding a workaround to undo this (so our keys are translated->untranslated->translated, likely a lossy process).
        // This won't cover edge cases but this is at least going to cover common cases.
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL) {
            return key
        }

        var resultKey = key
        val keyName = GLFW.glfwGetKeyName(key, scancode)
        eatErrors()
        if (keyName != null && keyName.length > 2 && keyName[0].code != 0 && keyName[1].code == 0) {
            if (keyName[0] >= '0' && keyName[0] <= '9') {
                resultKey = GLFW.GLFW_KEY_0 + (keyName[0].code - '0'.code)
            } else if (keyName[0] >= 'A' && keyName[0] <= 'Z') {
                resultKey = GLFW.GLFW_KEY_A + (keyName[0].code - 'A'.code)
            } else if (keyName[0] >= 'a' && keyName[0] <= 'z') {
                resultKey = GLFW.GLFW_KEY_A + (keyName[0].code - 'a'.code)
            } else {
                val index = props.charNames.indexOf(keyName[0])
                if (index != -1) {
                    resultKey = props.charKeys[index]
                }
            }
        }

        return resultKey
    }

    protected fun eatErrors() {
        if (glfwHasGetError) { // Eat errors (see #5908)
            val pb = MemoryUtil.memAllocPointer(1)
            GLFW.glfwGetError(pb)
            MemoryUtil.memFree(pb)
        }
    }

    fun keyCallback(window: Long, keycode: Int, scancode: Int, action: Int, mods: Int) {
        if (data!!.prevUserCallbackKey != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackKey!!.invoke(window, keycode, scancode, action, mods)
        }

        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) {
            return
        }

        updateKeyModifiers(window)

        if (keycode >= 0 && keycode < data!!.keyOwnerWindows.size) {
            data!!.keyOwnerWindows[keycode] = if ((action == GLFW.GLFW_PRESS)) window else -1
        }

        val key = translateUntranslatedKey(keycode, scancode)

        val io = ImGui.getIO()
        val imguiKey = glfwKeyToImGuiKey(key)
        io.addKeyEvent(imguiKey, (action == GLFW.GLFW_PRESS))
        io.setKeyEventNativeData(imguiKey, key, scancode) // To support legacy indexing (<1.87 user code)
    }

    fun windowFocusCallback(window: Long, focused: Boolean) {
        if (data!!.prevUserCallbackWindowFocus != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackWindowFocus!!.invoke(window, focused)
        }

        ImGui.getIO().addFocusEvent(focused)
    }

    fun cursorPosCallback(window: Long, x: Double, y: Double) {
        if (data!!.prevUserCallbackCursorPos != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackCursorPos!!.invoke(window, x, y)
        }

        var posX = x.toFloat()
        var posY = y.toFloat()

        val io = ImGui.getIO()

        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            GLFW.glfwGetWindowPos(window, props.windowX, props.windowY)
            posX += props.windowX[0].toFloat()
            posY += props.windowY[0].toFloat()
        }

        io.addMousePosEvent(posX, posY)
        data!!.lastValidMousePos[posX] = posY
    }

    // Workaround: X11 seems to send spurious Leave/Enter events which would make us lose our position,
    // so we back it up and restore on Leave/Enter (see https://github.com/ocornut/imgui/issues/4984)
    fun cursorEnterCallback(window: Long, entered: Boolean) {
        if (data!!.prevUserCallbackCursorEnter != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackCursorEnter!!.invoke(window, entered)
        }

        val io = ImGui.getIO()

        if (entered) {
            data!!.mouseWindow = window
            io.addMousePosEvent(data!!.lastValidMousePos.x, data!!.lastValidMousePos.y)
        } else if (data!!.mouseWindow == window) {
            io.getMousePos(data!!.lastValidMousePos)
            data!!.mouseWindow = -1
            io.addMousePosEvent(Float.MIN_VALUE, Float.MIN_VALUE)
        }
    }

    fun charCallback(window: Long, c: Int) {
        if (data!!.prevUserCallbackChar != null && shouldChainCallback(window)) {
            data!!.prevUserCallbackChar!!.invoke(window, c)
        }

        ImGui.getIO().addInputCharacter(c)
    }

    fun monitorCallback(window: Long, event: Int) {
        data!!.wantUpdateMonitors = true
    }

    fun installCallbacks(window: Long) {
        data!!.prevUserCallbackWindowFocus = GLFW.glfwSetWindowFocusCallback(window,
            ::windowFocusCallback)
        data!!.prevUserCallbackCursorEnter = GLFW.glfwSetCursorEnterCallback(window,
            ::cursorEnterCallback)
        data!!.prevUserCallbackCursorPos = GLFW.glfwSetCursorPosCallback(window,
            ::cursorPosCallback)
        data!!.prevUserCallbackMousebutton = GLFW.glfwSetMouseButtonCallback(window,
            ::mouseButtonCallback)
        data!!.prevUserCallbackScroll = GLFW.glfwSetScrollCallback(window, ::scrollCallback)
        data!!.prevUserCallbackKey = GLFW.glfwSetKeyCallback(window, ::keyCallback)
        data!!.prevUserCallbackChar = GLFW.glfwSetCharCallback(window, ::charCallback)
        data!!.prevUserCallbackMonitor = GLFW.glfwSetMonitorCallback(::monitorCallback)
        data!!.installedCallbacks = true
    }

    protected fun freeCallback(cb: Callback?) {
        cb?.free()
    }

    fun restoreCallbacks(window: Long) {
        freeCallback(GLFW.glfwSetWindowFocusCallback(window, data!!.prevUserCallbackWindowFocus))
        freeCallback(GLFW.glfwSetCursorEnterCallback(window, data!!.prevUserCallbackCursorEnter))
        freeCallback(GLFW.glfwSetCursorPosCallback(window, data!!.prevUserCallbackCursorPos))
        freeCallback(GLFW.glfwSetMouseButtonCallback(window, data!!.prevUserCallbackMousebutton))
        freeCallback(GLFW.glfwSetScrollCallback(window, data!!.prevUserCallbackScroll))
        freeCallback(GLFW.glfwSetKeyCallback(window, data!!.prevUserCallbackKey))
        freeCallback(GLFW.glfwSetCharCallback(window, data!!.prevUserCallbackChar))
        freeCallback(GLFW.glfwSetMonitorCallback(data!!.prevUserCallbackMonitor))
        data!!.installedCallbacks = false
        data!!.prevUserCallbackWindowFocus = null
        data!!.prevUserCallbackCursorEnter = null
        data!!.prevUserCallbackCursorPos = null
        data!!.prevUserCallbackMousebutton = null
        data!!.prevUserCallbackScroll = null
        data!!.prevUserCallbackKey = null
        data!!.prevUserCallbackChar = null
        data!!.prevUserCallbackMonitor = null
    }

    /**
     * Set to 'true' to enable chaining installed callbacks for all windows (including secondary viewports created by backends or by user.
     * This is 'false' by default meaning we only chain callbacks for the main viewport.
     * We cannot set this to 'true' by default because user callbacks code may be not testing the 'window' parameter of their callback.
     * If you set this to 'true' your user callback code will need to make sure you are testing the 'window' parameter.
     */
    fun setCallbacksChainForAllWindows(chainForAllWindows: Boolean) {
        data!!.callbacksChainForAllWindows = chainForAllWindows
    }

    protected fun newData(): Data {
        return Data()
    }

    fun init(window: Long, installCallbacks: Boolean): Boolean {
        val io = ImGui.getIO()

        io.backendPlatformName = "imgui-java_impl_glfw"
        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors or ImGuiBackendFlags.HasSetMousePos or ImGuiBackendFlags.PlatformHasViewports)
        if (glfwHasMousePassthrough || (glfwHasWindowHovered && IS_WINDOWS)) {
            io.addBackendFlags(ImGuiBackendFlags.HasMouseHoveredViewport)
        }

        data = newData()
        data!!.window = window
        data!!.time = 0.0
        data!!.wantUpdateMonitors = true

        io.setGetClipboardTextFn(clipboardTextFn)
        io.setSetClipboardTextFn(setClipboardTextFn())

        // Create mouse cursors
        // (By design, on X11 cursors are user configurable and some cursors may be missing. When a cursor doesn't exist,
        // GLFW will emit an error which will often be printed by the app, so we temporarily disable error reporting.
        // Missing cursors will return NULL and our _UpdateMouseCursor() function will use the Arrow cursor instead.)
        val prevErrorCallback = GLFW.glfwSetErrorCallback(null)
        data!!.mouseCursors[ImGuiMouseCursor.Arrow] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        data!!.mouseCursors[ImGuiMouseCursor.TextInput] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
        data!!.mouseCursors[ImGuiMouseCursor.ResizeNS] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
        data!!.mouseCursors[ImGuiMouseCursor.ResizeEW] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
        data!!.mouseCursors[ImGuiMouseCursor.Hand] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
        if (glfwHasNewCursors) {
            data!!.mouseCursors[ImGuiMouseCursor.ResizeAll] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.ResizeNESW] =
                GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.ResizeNWSE] =
                GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.NotAllowed] =
                GLFW.glfwCreateStandardCursor(GLFW.GLFW_NOT_ALLOWED_CURSOR)
        } else {
            data!!.mouseCursors[ImGuiMouseCursor.ResizeAll] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.ResizeNESW] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
            data!!.mouseCursors[ImGuiMouseCursor.NotAllowed] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        }
        GLFW.glfwSetErrorCallback(prevErrorCallback)
        eatErrors()

        // Chain GLFW callbacks: our callbacks will call the user's previously installed callbacks, if any.
        if (installCallbacks) {
            installCallbacks(window)
        }

        // Update monitors the first time (note: monitor callback are broken in GLFW 3.2 and earlier, see github.com/glfw/glfw/issues/784)
        updateMonitors()
        GLFW.glfwSetMonitorCallback(::monitorCallback)

        // Our mouse update function expect PlatformHandle to be filled for the main viewport
        val mainViewport = ImGui.getMainViewport()
        mainViewport.platformHandle = window
        if (IS_WINDOWS) {
            mainViewport.platformHandleRaw = GLFWNativeWin32.glfwGetWin32Window(window)
        }
        if (IS_APPLE) {
            mainViewport.platformHandleRaw = GLFWNativeCocoa.glfwGetCocoaWindow(window)
        }
        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            initPlatformInterface()
        }

        return true
    }

    fun shutdown() {
        val io = ImGui.getIO()

        shutdownPlatformInterface()

        if (data!!.installedCallbacks) {
            restoreCallbacks(data!!.window)
        }

        for (cursorN in 0 until ImGuiMouseCursor.COUNT) {
            GLFW.glfwDestroyCursor(data!!.mouseCursors[cursorN])
        }

        io.backendPlatformName = null
        data = null
        io.removeBackendFlags(
            ImGuiBackendFlags.HasMouseCursors or ImGuiBackendFlags.HasSetMousePos or ImGuiBackendFlags.HasGamepad
                    or ImGuiBackendFlags.PlatformHasViewports or ImGuiBackendFlags.HasMouseHoveredViewport
        )
    }

    protected fun updateMouseData() {
        val io = ImGui.getIO()
        val platformIO = ImGui.getPlatformIO()

        var mouseViewportId = 0
        io.getMousePos(props.mousePosPrev)

        for (n in 0 until platformIO.viewportsSize) {
            val viewport = platformIO.getViewports(n)
            val window = viewport.platformHandle
            val isWindowFocused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0

            if (isWindowFocused) {
                // (Optional) Set OS mouse position from Dear ImGui if requested (rarely used, only when ImGuiConfigFlags_NavEnableSetMousePos is enabled by user)
                // When multi-viewports are enabled, all Dear ImGui positions are same as OS positions.
                if (io.wantSetMousePos) {
                    GLFW.glfwSetCursorPos(
                        window,
                        (props.mousePosPrev.x - viewport.posX).toDouble(),
                        (props.mousePosPrev.y - viewport.posY).toDouble()
                    )
                }

                // (Optional) Fallback to provide mouse position when focused (ImGui_ImplGlfw_CursorPosCallback already provides this when hovered or captured)
                if (data!!.mouseWindow == -1L) {
                    GLFW.glfwGetCursorPos(window, props.mouseX, props.mouseY)
                    var mouseX = props.mouseX[0]
                    var mouseY = props.mouseY[0]
                    if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                        // Single viewport mode: mouse position in client window coordinates (io.MousePos is (0,0) when the mouse is on the upper-left corner of the app window)
                        // Multi-viewport mode: mouse position in OS absolute coordinates (io.MousePos is (0,0) when the mouse is on the upper-left of the primary monitor)
                        GLFW.glfwGetWindowPos(window, props.windowX, props.windowY)
                        mouseX += props.windowX[0].toDouble()
                        mouseY += props.windowY[0].toDouble()
                    }
                    data!!.lastValidMousePos[mouseX.toFloat()] = mouseY.toFloat()
                    io.addMousePosEvent(mouseX.toFloat(), mouseY.toFloat())
                }
            }

            // (Optional) When using multiple viewports: call io.AddMouseViewportEvent() with the viewport the OS mouse cursor is hovering.
            // If ImGuiBackendFlags_HasMouseHoveredViewport is not set by the backend, Dear imGui will ignore this field and infer the information using its flawed heuristic.
            // - [X] GLFW >= 3.3 backend ON WINDOWS ONLY does correctly ignore viewports with the _NoInputs flag.
            // - [!] GLFW <= 3.2 backend CANNOT correctly ignore viewports with the _NoInputs flag, and CANNOT reported Hovered Viewport because of mouse capture.
            //       Some backend are not able to handle that correctly. If a backend report an hovered viewport that has the _NoInputs flag (e.g. when dragging a window
            //       for docking, the viewport has the _NoInputs flag in order to allow us to find the viewport under), then Dear ImGui is forced to ignore the value reported
            //       by the backend, and use its flawed heuristic to guess the viewport behind.
            // - [X] GLFW backend correctly reports this regardless of another viewport behind focused and dragged from (we need this to find a useful drag and drop target).
            // FIXME: This is currently only correct on Win32. See what we do below with the WM_NCHITTEST, missing an equivalent for other systems.
            // See https://github.com/glfw/glfw/issues/1236 if you want to help in making this a GLFW feature.
            if (glfwHasMousePassthrough || (glfwHasWindowHovered && IS_WINDOWS)) {
                val windowNoInput = viewport.hasFlags(ImGuiViewportFlags.NoInputs)
                if (glfwHasMousePassthrough) {
                    GLFW.glfwSetWindowAttrib(
                        window,
                        GLFW.GLFW_MOUSE_PASSTHROUGH,
                        if (windowNoInput) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
                    )
                }
                if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE && !windowNoInput) {
                    mouseViewportId = viewport.id
                }
            }
            // else
            // We cannot use bd->MouseWindow maintained from CursorEnter/Leave callbacks, because it is locked to the window capturing mouse.
        }

        if (io.hasBackendFlags(ImGuiBackendFlags.HasMouseHoveredViewport)) {
            io.addMouseViewportEvent(mouseViewportId)
        }
    }

    protected fun updateMouseCursor() {
        val io = ImGui.getIO()

        if (io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange) || GLFW.glfwGetInputMode(
                data!!.window,
                GLFW.GLFW_CURSOR
            ) == GLFW.GLFW_CURSOR_DISABLED
        ) {
            return
        }

        val imguiCursor = ImGui.getMouseCursor()
        val platformIO = ImGui.getPlatformIO()

        for (n in 0 until platformIO.viewportsSize) {
            val windowPtr = platformIO.getViewports(n).platformHandle

            if (imguiCursor == ImGuiMouseCursor.None || io.mouseDrawCursor) {
                // Hide OS mouse cursor if imgui is drawing it or if it wants no cursor
                GLFW.glfwSetInputMode(windowPtr, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
            } else {
                // Show OS mouse cursor
                // FIXME-PLATFORM: Unfocused windows seems to fail changing the mouse cursor with GLFW 3.2, but 3.3 works here.
                GLFW.glfwSetCursor(
                    windowPtr,
                    if (data!!.mouseCursors[imguiCursor] != 0L) data!!.mouseCursors[imguiCursor] else data!!.mouseCursors[ImGuiMouseCursor.Arrow]
                )
                GLFW.glfwSetInputMode(windowPtr, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            }
        }
    }

    private fun interface MapButton {
        fun run(keyNo: Int, buttonNo: Int, _unused: Int)
    }

    private fun interface MapAnalog {
        fun run(keyNo: Int, axisNo: Int, _unused: Int, v0: Float, v1: Float)
    }

    private fun saturate(v: Float): Float {
        return if (v < 0.0f) 0.0f else if (v > 1.0f) 1.0f else v
    }

    protected fun updateGamepads() {
        val io = ImGui.getIO()

        if (!io.hasConfigFlags(ImGuiConfigFlags.NavEnableGamepad)) {
            return
        }

        io.removeBackendFlags(ImGuiBackendFlags.HasGamepad)

        val mapButton: MapButton
        val mapAnalog: MapAnalog

        if (glfwHasGamepadApi) {
            GLFWGamepadState.create().use { gamepad ->
                if (!GLFW.glfwGetGamepadState(GLFW.GLFW_JOYSTICK_1, gamepad)) {
                    return
                }
                mapButton =
                    MapButton { keyNo: Int, buttonNo: Int, _unused: Int ->
                        io.addKeyEvent(
                            keyNo,
                            gamepad.buttons(buttonNo).toInt() != 0
                        )
                    }
                mapAnalog =
                    MapAnalog { keyNo: Int, axisNo: Int, _unused: Int, v0: Float, v1: Float ->
                        var v: Float = gamepad.axes(axisNo)
                        v = (v - v0) / (v1 - v0)
                        io.addKeyAnalogEvent(keyNo, v > 0.10f, saturate(v))
                    }
            }
        } else {
            val axes = GLFW.glfwGetJoystickAxes(GLFW.GLFW_JOYSTICK_1)
            val buttons = GLFW.glfwGetJoystickButtons(GLFW.GLFW_JOYSTICK_1)
            if ((axes == null) || (axes.limit() == 0) || (buttons == null) || (buttons.limit() == 0)) {
                return
            }
            mapButton = MapButton { keyNo: Int, buttonNo: Int, _unused: Int ->
                io.addKeyEvent(
                    keyNo,
                    (buttons.limit() > buttonNo && buttons.get(buttonNo).toInt() == GLFW.GLFW_PRESS)
                )
            }
            mapAnalog = MapAnalog { keyNo: Int, axisNo: Int, _unused: Int, v0: Float, v1: Float ->
                var v: Float = if ((axes.limit() > axisNo)) axes.get(axisNo) else v0
                v = (v - v0) / (v1 - v0)
                io.addKeyAnalogEvent(keyNo, v > 0.10f, saturate(v))
            }
        }

        io.addBackendFlags(ImGuiBackendFlags.HasGamepad)
        mapButton.run(ImGuiKey.GamepadStart, GLFW.GLFW_GAMEPAD_BUTTON_START, 7)
        mapButton.run(ImGuiKey.GamepadBack, GLFW.GLFW_GAMEPAD_BUTTON_BACK, 6)
        mapButton.run(ImGuiKey.GamepadFaceLeft, GLFW.GLFW_GAMEPAD_BUTTON_X, 2) // Xbox X, PS Square
        mapButton.run(ImGuiKey.GamepadFaceRight, GLFW.GLFW_GAMEPAD_BUTTON_B, 1) // Xbox B, PS Circle
        mapButton.run(ImGuiKey.GamepadFaceUp, GLFW.GLFW_GAMEPAD_BUTTON_Y, 3) // Xbox Y, PS Triangle
        mapButton.run(ImGuiKey.GamepadFaceDown, GLFW.GLFW_GAMEPAD_BUTTON_A, 0) // Xbox A, PS Cross
        mapButton.run(ImGuiKey.GamepadDpadLeft, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT, 13)
        mapButton.run(ImGuiKey.GamepadDpadRight, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, 11)
        mapButton.run(ImGuiKey.GamepadDpadUp, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP, 10)
        mapButton.run(ImGuiKey.GamepadDpadDown, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, 12)
        mapButton.run(ImGuiKey.GamepadL1, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, 4)
        mapButton.run(ImGuiKey.GamepadR1, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, 5)
        mapAnalog.run(ImGuiKey.GamepadL2, GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, 4, -0.75f, +1.0f)
        mapAnalog.run(ImGuiKey.GamepadR2, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, 5, -0.75f, +1.0f)
        mapButton.run(ImGuiKey.GamepadL3, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB, 8)
        mapButton.run(ImGuiKey.GamepadR3, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, 9)
        mapAnalog.run(ImGuiKey.GamepadLStickLeft, GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, 0, -0.25f, -1.0f)
        mapAnalog.run(ImGuiKey.GamepadLStickRight, GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, 0, +0.25f, +1.0f)
        mapAnalog.run(ImGuiKey.GamepadLStickUp, GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, 1, -0.25f, -1.0f)
        mapAnalog.run(ImGuiKey.GamepadLStickDown, GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, 1, +0.25f, +1.0f)
        mapAnalog.run(ImGuiKey.GamepadRStickLeft, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, 2, -0.25f, -1.0f)
        mapAnalog.run(ImGuiKey.GamepadRStickRight, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, 2, +0.25f, +1.0f)
        mapAnalog.run(ImGuiKey.GamepadRStickUp, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, 3, -0.25f, -1.0f)
        mapAnalog.run(ImGuiKey.GamepadRStickDown, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, 3, +0.25f, +1.0f)
    }

    protected fun updateMonitors() {
        val platformIO = ImGui.getPlatformIO()
        data!!.wantUpdateMonitors = false

        val monitors = GLFW.glfwGetMonitors()
        if (monitors == null) {
            System.err.println("Unable to get monitors!")
            return
        }
        if (monitors.limit() == 0) { // Preserve existing monitor list if there are none. Happens on macOS sleeping (#5683)
            return
        }

        platformIO.resizeMonitors(0)

        for (n in 0 until monitors.limit()) {
            val monitor = monitors[n]

            val vidMode = GLFW.glfwGetVideoMode(monitor) ?: continue

            GLFW.glfwGetMonitorPos(monitor, props.monitorX, props.monitorY)

            val mainPosX = props.monitorX[0].toFloat()
            val mainPosY = props.monitorY[0].toFloat()
            val mainSizeX = vidMode.width().toFloat()
            val mainSizeY = vidMode.height().toFloat()

            var workPosX = 0f
            var workPosY = 0f
            var workSizeX = 0f
            var workSizeY = 0f

            // Workaround a small GLFW issue reporting zero on monitor changes: https://github.com/glfw/glfw/pull/1761
            if (glfwHasMonitorWorkArea) {
                GLFW.glfwGetMonitorWorkarea(
                    monitor,
                    props.monitorWorkAreaX,
                    props.monitorWorkAreaY,
                    props.monitorWorkAreaWidth,
                    props.monitorWorkAreaHeight
                )
                if (props.monitorWorkAreaWidth[0] > 0 && props.monitorWorkAreaHeight[0] > 0) {
                    workPosX = props.monitorWorkAreaX[0].toFloat()
                    workPosY = props.monitorWorkAreaY[0].toFloat()
                    workSizeX = props.monitorWorkAreaWidth[0].toFloat()
                    workSizeY = props.monitorWorkAreaHeight[0].toFloat()
                }
            }

            var dpiScale = 0f

            // Warning: the validity of monitor DPI information on Windows depends on the application DPI awareness settings,
            // which generally needs to be set in the manifest or at runtime.
            if (glfwHasPerMonitorDpi) {
                GLFW.glfwGetMonitorContentScale(monitor, props.monitorContentScaleX, props.monitorContentScaleY)
                dpiScale = props.monitorContentScaleX[0]
            }

            platformIO.pushMonitors(
                monitor,
                mainPosX,
                mainPosY,
                mainSizeX,
                mainSizeY,
                workPosX,
                workPosY,
                workSizeX,
                workSizeY,
                dpiScale
            )
        }
    }

    fun newFrame(width: Int, height: Int) {
        val io = ImGui.getIO()

        // Setup display size (every frame to accommodate for window resizing)
        GLFW.glfwGetWindowSize(data!!.window, props.windowW, props.windowH)
        GLFW.glfwGetFramebufferSize(data!!.window, props.displayW, props.displayH)
        props.displayW[0] = width
        props.displayH[0] = height
        io.setDisplaySize(props.windowW[0].toFloat(), props.windowH[0].toFloat())
        if (props.windowW[0] > 0 && props.windowH[0] > 0) {
            val scaleX = props.displayW[0].toFloat() / props.windowW[0]
            val scaleY = props.displayH[0].toFloat() / props.windowH[0]
            io.setDisplayFramebufferScale(scaleX, scaleY)
        }

        if (data!!.wantUpdateMonitors) {
            updateMonitors()
        }

        // Setup time step
        // (Accept glfwGetTime() not returning a monotonically increasing value. Seems to happens on disconnecting peripherals and probably on VMs and Emscripten, see #6491, #6189, #6114, #3644)
        var currentTime = GLFW.glfwGetTime()
        if (currentTime <= data!!.time) {
            currentTime = data!!.time + 0.00001f
        }
        io.deltaTime = if (data!!.time > 0.0) (currentTime - data!!.time).toFloat() else 1.0f / 60.0f
        data!!.time = currentTime

        updateMouseData()
        updateMouseCursor()

        // Update game controllers (if enabled and available)
        updateGamepads()
    }

    //--------------------------------------------------------------------------------------------------------
    // MULTI-VIEWPORT / PLATFORM INTERFACE SUPPORT
    // This is an _advanced_ and _optional_ feature, allowing the backend to create and handle multiple viewports simultaneously.
    // If you are new to dear imgui or creating a new binding for dear imgui, it is recommended that you completely ignore this section first..
    //--------------------------------------------------------------------------------------------------------
    private class ViewportData() {
        var window: Long = -1
        var windowOwned: Boolean = false
        var ignoreWindowPosEventFrame: Int = -1
        var ignoreWindowSizeEventFrame: Int = -1
    }

    private fun windowCloseCallback(windowId: Long) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        if (vp.isValidPtr) {
            vp.platformRequestClose = true
        }
    }

    // GLFW may dispatch window pos/size events after calling glfwSetWindowPos()/glfwSetWindowSize().
    // However: depending on the platform the callback may be invoked at different time:
    // - on Windows it appears to be called within the glfwSetWindowPos()/glfwSetWindowSize() call
    // - on Linux it is queued and invoked during glfwPollEvents()
    // Because the event doesn't always fire on glfwSetWindowXXX() we use a frame counter tag to only
    // ignore recent glfwSetWindowXXX() calls.
    private fun windowPosCallback(windowId: Long, xPos: Int, yPos: Int) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        if (vp.isNotValidPtr) {
            return
        }

        val vd: ViewportData? = vp.platformUserData as ViewportData
        if (vd != null) {
            val ignoreEvent = (ImGui.getFrameCount() <= vd.ignoreWindowPosEventFrame + 1)
            if (ignoreEvent) {
                return
            }
        }

        vp.platformRequestMove = true
    }

    private fun windowSizeCallback(windowId: Long, width: Int, height: Int) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        if (vp.isNotValidPtr) {
            return
        }

        val vd: ViewportData? = vp.platformUserData as ViewportData
        if (vd != null) {
            val ignoreEvent = (ImGui.getFrameCount() <= vd.ignoreWindowSizeEventFrame + 1)
            if (ignoreEvent) {
                return
            }
        }

        vp.platformRequestResize = true
    }

    private inner class CreateWindowFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val vd = ViewportData()
            vp.platformUserData = vd

            // GLFW 3.2 unfortunately always set focus on glfwCreateWindow() if GLFW_VISIBLE is set, regardless of GLFW_FOCUSED
            // With GLFW 3.3, the hint GLFW_FOCUS_ON_SHOW fixes this problem
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE)
            if (glfwHasFocusOnShow) {
                GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE)
            }
            GLFW.glfwWindowHint(
                GLFW.GLFW_DECORATED,
                if (vp.hasFlags(ImGuiViewportFlags.NoDecoration)) GLFW.GLFW_FALSE else GLFW.GLFW_TRUE
            )
            if (glfwHawWindowTopmost) {
                GLFW.glfwWindowHint(
                    GLFW.GLFW_FLOATING,
                    if (vp.hasFlags(ImGuiViewportFlags.TopMost)) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
                )
            }

            vd.window = GLFW.glfwCreateWindow(
                vp.sizeX.toInt(),
                vp.sizeY.toInt(),
                "No Title Yet",
                MemoryUtil.NULL,
                data!!.window
            )
            vd.windowOwned = true

            vp.platformHandle = vd.window

            if (IS_WINDOWS) {
                vp.platformHandleRaw = GLFWNativeWin32.glfwGetWin32Window(vd.window)
            } else if (IS_APPLE) {
                vp.platformHandleRaw = GLFWNativeCocoa.glfwGetCocoaWindow(vd.window)
            }

            GLFW.glfwSetWindowPos(vd.window, vp.posX.toInt(), vp.posY.toInt())

            // Install GLFW callbacks for secondary viewports
            GLFW.glfwSetWindowFocusCallback(vd.window, ::windowFocusCallback)
            GLFW.glfwSetCursorEnterCallback(vd.window, ::cursorEnterCallback)
            GLFW.glfwSetCursorPosCallback(vd.window, ::cursorPosCallback)
            GLFW.glfwSetMouseButtonCallback(vd.window, ::mouseButtonCallback)
            GLFW.glfwSetScrollCallback(vd.window, ::scrollCallback)
            GLFW.glfwSetKeyCallback(vd.window, ::keyCallback)
            GLFW.glfwSetCharCallback(vd.window, ::charCallback)
            GLFW.glfwSetWindowCloseCallback(vd.window, ::windowCloseCallback)
            GLFW.glfwSetWindowPosCallback(vd.window, ::windowPosCallback)
            GLFW.glfwSetWindowSizeCallback(vd.window, ::windowSizeCallback)

            GLFW.glfwMakeContextCurrent(vd.window)
            GLFW.glfwSwapInterval(0)
        }
    }

    private inner class DestroyWindowFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val vd: ViewportData? = vp.platformUserData as ViewportData

            if (vd != null && vd.windowOwned) {
                if (!glfwHasMousePassthrough && glfwHasWindowHovered && IS_WINDOWS) {
                    // TODO: RemovePropA
                }

                // Release any keys that were pressed in the window being destroyed and are still held down,
                // because we will not receive any release events after window is destroyed.
                for (i in data!!.keyOwnerWindows.indices) {
                    if (data!!.keyOwnerWindows[i] == vd.window) {
                        keyCallback(
                            vd.window,
                            i,
                            0,
                            GLFW.GLFW_RELEASE,
                            0
                        ) // Later params are only used for main viewport, on which this function is never called.
                    }
                }

                Callbacks.glfwFreeCallbacks(vd.window)
                GLFW.glfwDestroyWindow(vd.window)

                vd.window = -1
            }


            vp.platformHandle = -1
            vp.platformUserData = null
        }
    }

    private class ShowWindowFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val vd: ViewportData = vp.platformUserData as ViewportData
                ?: return

            if (IS_WINDOWS && vp.hasFlags(ImGuiViewportFlags.NoTaskBarIcon)) {
                ImGuiImplGlfwNative.win32hideFromTaskBar(vp.platformHandleRaw)
            }

            GLFW.glfwShowWindow(vd.window)
        }
    }

    private class GetWindowPosFunction() : ImPlatformFuncViewportSuppImVec2() {
        private val posX = IntArray(1)
        private val posY = IntArray(1)

        override fun get(vp: ImGuiViewport, dst: ImVec2) {
            val vd: ViewportData = vp.platformUserData as ViewportData
                ?: return
            posX[0] = 0
            posY[0] = 0
            GLFW.glfwGetWindowPos(vd.window, posX, posY)
            dst[posX[0].toFloat()] = posY[0].toFloat()
        }
    }

    private class SetWindowPosFunction() : ImPlatformFuncViewportImVec2() {
        override fun accept(vp: ImGuiViewport, value: ImVec2) {
            val vd: ViewportData = vp.platformUserData as ViewportData
                ?: return
            vd.ignoreWindowPosEventFrame = ImGui.getFrameCount()
            GLFW.glfwSetWindowPos(vd.window, value.x.toInt(), value.y.toInt())
        }
    }

    private class GetWindowSizeFunction() : ImPlatformFuncViewportSuppImVec2() {
        private val width = IntArray(1)
        private val height = IntArray(1)

        override fun get(vp: ImGuiViewport, dst: ImVec2) {
            val vd: ViewportData = vp.platformUserData as ViewportData
                ?: return
            width[0] = 0
            height[0] = 0
            GLFW.glfwGetWindowSize(vd.window, width, height)
            dst.x = width[0].toFloat()
            dst.y = height[0].toFloat()
        }
    }

    private inner class SetWindowSizeFunction() : ImPlatformFuncViewportImVec2() {
        private val x = IntArray(1)
        private val y = IntArray(1)
        private val width = IntArray(1)
        private val height = IntArray(1)

        override fun accept(vp: ImGuiViewport, value: ImVec2) {
            val vd: ViewportData = vp.platformUserData as ViewportData
                ?: return
            if (IS_APPLE && !glfwHasOsxWindowPosFix) {
                // Native OS windows are positioned from the bottom-left corner on macOS, whereas on other platforms they are
                // positioned from the upper-left corner. GLFW makes an effort to convert macOS style coordinates, however it
                // doesn't handle it when changing size. We are manually moving the window in order for changes of size to be based
                // on the upper-left corner.
                x[0] = 0
                y[0] = 0
                width[0] = 0
                height[0] = 0
                GLFW.glfwGetWindowPos(vd.window, x, y)
                GLFW.glfwGetWindowSize(vd.window, width, height)
                GLFW.glfwSetWindowPos(vd.window, x[0], y[0] - height[0] + value.y.toInt())
            }
            vd.ignoreWindowSizeEventFrame = ImGui.getFrameCount()
            GLFW.glfwSetWindowSize(vd.window, value.x.toInt(), value.y.toInt())
        }
    }

    private class SetWindowTitleFunction() : ImPlatformFuncViewportString() {
        override fun accept(vp: ImGuiViewport, value: String) {
            val vd: ViewportData? = vp.platformUserData as ViewportData
            if (vd != null) {
                GLFW.glfwSetWindowTitle(vd.window, value)
            }
        }
    }

    private inner class SetWindowFocusFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            if (glfwHasFocusWindow) {
                val vd: ViewportData? = vp.platformUserData as ViewportData
                if (vd != null) {
                    GLFW.glfwFocusWindow(vd.window)
                }
            }
        }
    }

    private class GetWindowFocusFunction() : ImPlatformFuncViewportSuppBoolean() {
        override fun get(vp: ImGuiViewport): Boolean {
            val data = vp.platformUserData as ViewportData
            return GLFW.glfwGetWindowAttrib(data.window, GLFW.GLFW_FOCUSED) != 0
        }
    }

    private class GetWindowMinimizedFunction() : ImPlatformFuncViewportSuppBoolean() {
        override fun get(vp: ImGuiViewport): Boolean {
            val vd: ViewportData? = vp.platformUserData as ViewportData
            if (vd != null) {
                return GLFW.glfwGetWindowAttrib(vd.window, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_FALSE
            }
            return false
        }
    }

    private inner class SetWindowAlphaFunction() : ImPlatformFuncViewportFloat() {
        override fun accept(vp: ImGuiViewport, value: Float) {
            if (glfwHasWindowAlpha) {
                val vd: ViewportData? = vp.platformUserData as ViewportData
                if (vd != null) {
                    GLFW.glfwSetWindowOpacity(vd.window, value)
                }
            }
        }
    }

    private class RenderWindowFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val vd: ViewportData? = vp.platformUserData as ViewportData
            if (vd != null) {
                GLFW.glfwMakeContextCurrent(vd.window)
            }
        }
    }

    private class SwapBuffersFunction() : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val vd: ViewportData? = vp.platformUserData as ViewportData
            if (vd != null) {
                GLFW.glfwMakeContextCurrent(vd.window)
                GLFW.glfwSwapBuffers(vd.window)
            }
        }
    }

    protected fun initPlatformInterface() {
        val platformIO = ImGui.getPlatformIO()

        // Register platform interface (will be coupled with a renderer interface)
        platformIO.setPlatformCreateWindow(CreateWindowFunction())
        platformIO.setPlatformDestroyWindow(DestroyWindowFunction())
        platformIO.setPlatformShowWindow(ShowWindowFunction())
        platformIO.setPlatformGetWindowPos(GetWindowPosFunction())
        platformIO.setPlatformSetWindowPos(SetWindowPosFunction())
        platformIO.setPlatformGetWindowSize(GetWindowSizeFunction())
        platformIO.setPlatformSetWindowSize(SetWindowSizeFunction())
        platformIO.setPlatformSetWindowTitle(SetWindowTitleFunction())
        platformIO.setPlatformSetWindowFocus(SetWindowFocusFunction())
        platformIO.setPlatformGetWindowFocus(GetWindowFocusFunction())
        platformIO.setPlatformGetWindowMinimized(GetWindowMinimizedFunction())
        platformIO.setPlatformSetWindowAlpha(SetWindowAlphaFunction())
        platformIO.setPlatformRenderWindow(RenderWindowFunction())
        platformIO.setPlatformSwapBuffers(SwapBuffersFunction())

        // Register main window handle (which is owned by the main application, not by us)
        // This is mostly for simplicity and consistency, so that our code (e.g. mouse handling etc.) can use same logic for main and secondary viewports.
        val mainViewport = ImGui.getMainViewport()
        val vd = ViewportData()
        vd.window = data!!.window
        vd.windowOwned = false
        mainViewport.platformUserData = vd
        mainViewport.platformHandle = data!!.window
    }

    protected fun shutdownPlatformInterface() {
        ImGui.destroyPlatformWindows()
    }

    companion object {
        protected val OS: String = System.getProperty("os.name", "generic").lowercase(Locale.getDefault())
        protected val IS_WINDOWS: Boolean = OS.contains("win")
        protected val IS_APPLE: Boolean = OS.contains("mac") || OS.contains("darwin")

        // We gather version tests as define in order to easily see which features are version-dependent.
        protected val glfwVersionCombined: Int =
            (GLFW.GLFW_VERSION_MAJOR * 1000) + (GLFW.GLFW_VERSION_MINOR * 100) + GLFW.GLFW_VERSION_REVISION
        protected val glfwHawWindowTopmost: Boolean = glfwVersionCombined >= 3200 // 3.2+ GLFW_FLOATING
        protected val glfwHasWindowHovered: Boolean = glfwVersionCombined >= 3300 // 3.3+ GLFW_HOVERED
        protected val glfwHasWindowAlpha: Boolean = glfwVersionCombined >= 3300 // 3.3+ glfwSetWindowOpacity
        protected val glfwHasPerMonitorDpi: Boolean = glfwVersionCombined >= 3300 // 3.3+ glfwGetMonitorContentScale

        // protected boolean glfwHasVulkan; TODO: I want to believe...
        protected val glfwHasFocusWindow: Boolean = glfwVersionCombined >= 3200 // 3.2+ glfwFocusWindow
        protected val glfwHasFocusOnShow: Boolean = glfwVersionCombined >= 3300 // 3.3+ GLFW_FOCUS_ON_SHOW
        protected val glfwHasMonitorWorkArea: Boolean = glfwVersionCombined >= 3300 // 3.3+ glfwGetMonitorWorkarea
        protected val glfwHasOsxWindowPosFix: Boolean =
            glfwVersionCombined >= 3301 // 3.3.1+ Fixed: Resizing window repositions it on MacOS #1553
        protected val glfwHasNewCursors: Boolean =
            glfwVersionCombined >= 3400 // 3.4+ GLFW_RESIZE_ALL_CURSOR, GLFW_RESIZE_NESW_CURSOR, GLFW_RESIZE_NWSE_CURSOR, GLFW_NOT_ALLOWED_CURSOR
        protected val glfwHasMousePassthrough: Boolean = glfwVersionCombined >= 3400 // 3.4+ GLFW_MOUSE_PASSTHROUGH
        protected val glfwHasGamepadApi: Boolean = glfwVersionCombined >= 3300 // 3.3+ glfwGetGamepadState() new api
        protected val glfwHasGetKeyName: Boolean = glfwVersionCombined >= 3200 // 3.2+ glfwGetKeyName()
        protected val glfwHasGetError: Boolean = glfwVersionCombined >= 3300 // 3.3+ glfwGetError()
    }
}