package de.hanno.hpengine.graphics.editor

import imgui.*
import imgui.callback.*
import imgui.flag.*
import imgui.lwjgl3.glfw.ImGuiImplGlfwNative
import org.lwjgl.glfw.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*

/**
 * This class is a straightforward port of the
 * [imgui_impl_glfw.cpp](https://raw.githubusercontent.com/ocornut/imgui/256594575d95d56dda616c544c509740e74906b4/backends/imgui_impl_glfw.cpp).
 *
 *
 * It supports clipboard, gamepad, mouse and keyboard in the same way the original Dear ImGui code does. You can copy-paste this class in your codebase and
 * modify the rendering routine in the way you'd like.
 */
// I needed to fork the ImGuiImplGlfwF class because there was no way to pass in the
// size of the actual rendertarget that is used to render the UI into. The newFrame
// method is what needed attention, now I can pass in the dimensions there.
class ImGuiImplGlfwFrameBufferAware {
    // Pointer of the current GLFW window
    private var windowPtr: Long = 0

    // Some features may be available only from a specific version
    private var glfwHawWindowTopmost = false
    private var glfwHasWindowAlpha = false
    private var glfwHasPerMonitorDpi = false
    private var glfwHasFocusWindow = false
    private var glfwHasFocusOnShow = false
    private var glfwHasMonitorWorkArea = false
    private val glfwHasOsxWindowPosFix = false

    // For application window properties
    private val winWidth = IntArray(1)
    private val winHeight = IntArray(1)
    val fbWidth = IntArray(1)
    val fbHeight = IntArray(1)

    // Mouse cursors provided by GLFW
    private val mouseCursors = LongArray(ImGuiMouseCursor.COUNT)
    private val keyOwnerWindows = LongArray(512)

    // Empty array to fill ImGuiIO.NavInputs with zeroes
    private val emptyNavInputs = FloatArray(ImGuiNavInput.COUNT)

    // For mouse tracking
    private val mouseJustPressed = BooleanArray(ImGuiMouseButton.COUNT)
    private val mousePosBackup = ImVec2()
    private val mouseX = DoubleArray(1)
    private val mouseY = DoubleArray(1)
    private val windowX = IntArray(1)
    private val windowY = IntArray(1)

    // Monitor properties
    private val monitorX = IntArray(1)
    private val monitorY = IntArray(1)
    private val monitorWorkAreaX = IntArray(1)
    private val monitorWorkAreaY = IntArray(1)
    private val monitorWorkAreaWidth = IntArray(1)
    private val monitorWorkAreaHeight = IntArray(1)
    private val monitorContentScaleX = FloatArray(1)
    private val monitorContentScaleY = FloatArray(1)

    // GLFW callbacks
    private var prevUserCallbackWindowFocus: GLFWWindowFocusCallback? = null
    private var prevUserCallbackMouseButton: GLFWMouseButtonCallback? = null
    private var prevUserCallbackScroll: GLFWScrollCallback? = null
    private var prevUserCallbackKey: GLFWKeyCallback? = null
    private var prevUserCallbackChar: GLFWCharCallback? = null
    private var prevUserCallbackMonitor: GLFWMonitorCallback? = null
    private var prevUserCallbackCursorEnter: GLFWCursorEnterCallback? = null

    // Internal data
    private var callbacksInstalled = false
    private var wantUpdateMonitors = true
    private var time = 0.0
    private var mouseWindowPtr: Long = 0

    /**
     * Method to set the [GLFWMouseButtonCallback].
     *
     * @param windowId pointer to the window
     * @param button   clicked mouse button
     * @param action   click action type
     * @param mods     click modifiers
     */
    fun mouseButtonCallback(windowId: Long, button: Int, action: Int, mods: Int) {
        if (prevUserCallbackMouseButton != null && windowId == windowPtr) {
            prevUserCallbackMouseButton!!.invoke(windowId, button, action, mods)
        }
        if (action == GLFW.GLFW_PRESS && button >= 0 && button < mouseJustPressed.size) {
            mouseJustPressed[button] = true
        }
    }

    /**
     * Method to set the [GLFWScrollCallback].
     *
     * @param windowId pointer to the window
     * @param xOffset  scroll offset by x-axis
     * @param yOffset  scroll offset by y-axis
     */
    fun scrollCallback(windowId: Long, xOffset: Double, yOffset: Double) {
        if (prevUserCallbackScroll != null && windowId == windowPtr) {
            prevUserCallbackScroll!!.invoke(windowId, xOffset, yOffset)
        }
        val io = ImGui.getIO()
        io.mouseWheelH = io.mouseWheelH + xOffset.toFloat()
        io.mouseWheel = io.mouseWheel + yOffset.toFloat()
    }

    /**
     * Method to set the [GLFWKeyCallback].
     *
     * @param windowId pointer to the window
     * @param key      pressed key
     * @param scancode key scancode
     * @param action   press action
     * @param mods     press modifiers
     */
    fun keyCallback(windowId: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (prevUserCallbackKey != null && windowId == windowPtr) {
            prevUserCallbackKey!!.invoke(windowId, key, scancode, action, mods)
        }
        val io = ImGui.getIO()
        if (key >= 0 && key < keyOwnerWindows.size) {
            if (action == GLFW.GLFW_PRESS) {
                io.setKeysDown(key, true)
                keyOwnerWindows[key] = windowId
            } else if (action == GLFW.GLFW_RELEASE) {
                io.setKeysDown(key, false)
                keyOwnerWindows[key] = 0
            }
        }
        io.keyCtrl =
            io.getKeysDown(GLFW.GLFW_KEY_LEFT_CONTROL) || io.getKeysDown(GLFW.GLFW_KEY_RIGHT_CONTROL)
        io.keyShift =
            io.getKeysDown(GLFW.GLFW_KEY_LEFT_SHIFT) || io.getKeysDown(GLFW.GLFW_KEY_RIGHT_SHIFT)
        io.keyAlt =
            io.getKeysDown(GLFW.GLFW_KEY_LEFT_ALT) || io.getKeysDown(GLFW.GLFW_KEY_RIGHT_ALT)
        io.keySuper =
            io.getKeysDown(GLFW.GLFW_KEY_LEFT_SUPER) || io.getKeysDown(GLFW.GLFW_KEY_RIGHT_SUPER)
    }

    /**
     * Method to set the [GLFWWindowFocusCallback].
     *
     * @param windowId pointer to the window
     * @param focused  is window focused
     */
    fun windowFocusCallback(windowId: Long, focused: Boolean) {
        if (prevUserCallbackWindowFocus != null && windowId == windowPtr) {
            prevUserCallbackWindowFocus!!.invoke(windowId, focused)
        }
        ImGui.getIO().addFocusEvent(focused)
    }

    /**
     * Method to set the [GLFWCursorEnterCallback].
     *
     * @param windowId pointer to the window
     * @param entered  is cursor entered
     */
    fun cursorEnterCallback(windowId: Long, entered: Boolean) {
        if (prevUserCallbackCursorEnter != null && windowId == windowPtr) {
            prevUserCallbackCursorEnter!!.invoke(windowId, entered)
        }
        if (entered) {
            mouseWindowPtr = windowId
        }
        if (!entered && mouseWindowPtr == windowId) {
            mouseWindowPtr = 0
        }
    }

    /**
     * Method to set the [GLFWCharCallback].
     *
     * @param windowId pointer to the window
     * @param c        pressed char
     */
    fun charCallback(windowId: Long, c: Int) {
        if (prevUserCallbackChar != null && windowId == windowPtr) {
            prevUserCallbackChar!!.invoke(windowId, c)
        }
        val io = ImGui.getIO()
        io.addInputCharacter(c)
    }

    /**
     * Method to set the [GLFWMonitorCallback].
     *
     * @param windowId pointer to the window
     * @param event    monitor event type (ignored)
     */
    fun monitorCallback(windowId: Long, event: Int) {
        wantUpdateMonitors = true
    }

    /**
     * Method to do an initialization of the [ImGuiImplGlfw] state. It SHOULD be called before calling the [ImGuiImplGlfw.newFrame] method.
     *
     *
     * Method takes two arguments, which should be a valid GLFW window pointer and a boolean indicating whether or not to install callbacks.
     *
     * @param windowId         pointer to the window
     * @param installCallbacks should window callbacks be installed
     * @return true if everything initialized
     */
    fun init(windowId: Long, installCallbacks: Boolean): Boolean {
        windowPtr = windowId
        detectGlfwVersionAndEnabledFeatures()
        val io = ImGui.getIO()
        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors or ImGuiBackendFlags.HasSetMousePos or ImGuiBackendFlags.PlatformHasViewports)
        io.backendPlatformName = "imgui_java_impl_glfw"

        // Keyboard mapping. ImGui will use those indices to peek into the io.KeysDown[] array.
        val keyMap = IntArray(ImGuiKey.COUNT)
        keyMap[ImGuiKey.Tab] = GLFW.GLFW_KEY_TAB
        keyMap[ImGuiKey.LeftArrow] = GLFW.GLFW_KEY_LEFT
        keyMap[ImGuiKey.RightArrow] = GLFW.GLFW_KEY_RIGHT
        keyMap[ImGuiKey.UpArrow] = GLFW.GLFW_KEY_UP
        keyMap[ImGuiKey.DownArrow] = GLFW.GLFW_KEY_DOWN
        keyMap[ImGuiKey.PageUp] = GLFW.GLFW_KEY_PAGE_UP
        keyMap[ImGuiKey.PageDown] = GLFW.GLFW_KEY_PAGE_DOWN
        keyMap[ImGuiKey.Home] = GLFW.GLFW_KEY_HOME
        keyMap[ImGuiKey.End] = GLFW.GLFW_KEY_END
        keyMap[ImGuiKey.Insert] = GLFW.GLFW_KEY_INSERT
        keyMap[ImGuiKey.Delete] = GLFW.GLFW_KEY_DELETE
        keyMap[ImGuiKey.Backspace] = GLFW.GLFW_KEY_BACKSPACE
        keyMap[ImGuiKey.Space] = GLFW.GLFW_KEY_SPACE
        keyMap[ImGuiKey.Enter] = GLFW.GLFW_KEY_ENTER
        keyMap[ImGuiKey.Escape] = GLFW.GLFW_KEY_ESCAPE
        keyMap[ImGuiKey.KeyPadEnter] = GLFW.GLFW_KEY_KP_ENTER
        keyMap[ImGuiKey.A] = GLFW.GLFW_KEY_A
        keyMap[ImGuiKey.C] = GLFW.GLFW_KEY_C
        keyMap[ImGuiKey.V] = GLFW.GLFW_KEY_V
        keyMap[ImGuiKey.X] = GLFW.GLFW_KEY_X
        keyMap[ImGuiKey.Y] = GLFW.GLFW_KEY_Y
        keyMap[ImGuiKey.Z] = GLFW.GLFW_KEY_Z
        io.setKeyMap(keyMap)
        io.setGetClipboardTextFn(object : ImStrSupplier() {
            override fun get(): String {
                val clipboardString = GLFW.glfwGetClipboardString(windowId)
                return clipboardString ?: ""
            }
        })
        io.setSetClipboardTextFn(object : ImStrConsumer() {
            override fun accept(str: String) {
                GLFW.glfwSetClipboardString(windowId, str)
            }
        })

        // Mouse cursors mapping. Disable errors whilst setting due to X11.
        val prevErrorCallback = GLFW.glfwSetErrorCallback(null)
        mouseCursors[ImGuiMouseCursor.Arrow] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        mouseCursors[ImGuiMouseCursor.TextInput] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
        mouseCursors[ImGuiMouseCursor.ResizeAll] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        mouseCursors[ImGuiMouseCursor.ResizeNS] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
        mouseCursors[ImGuiMouseCursor.ResizeEW] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
        mouseCursors[ImGuiMouseCursor.ResizeNESW] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        mouseCursors[ImGuiMouseCursor.ResizeNWSE] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        mouseCursors[ImGuiMouseCursor.Hand] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
        mouseCursors[ImGuiMouseCursor.NotAllowed] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
        GLFW.glfwSetErrorCallback(prevErrorCallback)
        if (installCallbacks) {
            callbacksInstalled = true
            prevUserCallbackWindowFocus = GLFW.glfwSetWindowFocusCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::windowFocusCallback)
            prevUserCallbackCursorEnter = GLFW.glfwSetCursorEnterCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::cursorEnterCallback)
            prevUserCallbackMouseButton = GLFW.glfwSetMouseButtonCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::mouseButtonCallback)
            prevUserCallbackScroll = GLFW.glfwSetScrollCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::scrollCallback)
            prevUserCallbackKey = GLFW.glfwSetKeyCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::keyCallback)
            prevUserCallbackChar = GLFW.glfwSetCharCallback(windowId, this@ImGuiImplGlfwFrameBufferAware::charCallback)
        }
        // Update monitors the first time (note: monitor callback are broken in GLFW 3.2 and earlier, see github.com/glfw/glfw/issues/784)
        updateMonitors()
        prevUserCallbackMonitor = GLFW.glfwSetMonitorCallback(::monitorCallback)


        // Our mouse update function expect PlatformHandle to be filled for the main viewport
        val mainViewport = ImGui.getMainViewport()
        mainViewport.platformHandle = windowPtr
        if (IS_WINDOWS) {
            mainViewport.platformHandleRaw = GLFWNativeWin32.glfwGetWin32Window(windowId)
        }
        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            initPlatformInterface()
        }
        return true
    }

    /**
     * Updates [ImGuiIO] and [org.lwjgl.glfw.GLFW] state.
     */
    fun newFrame(width: Int, height: Int) {
        val io = ImGui.getIO()
        GLFW.glfwGetWindowSize(windowPtr, winWidth, winHeight)
        GLFW.glfwGetFramebufferSize(windowPtr, fbWidth, fbHeight)
        fbWidth[0] = width
        fbHeight[0] = height
        io.setDisplaySize(winWidth[0].toFloat(), winHeight[0].toFloat())
        if (winWidth[0] > 0 && winHeight[0] > 0) {
            val scaleX = fbWidth[0].toFloat() / winWidth[0]
            val scaleY = fbHeight[0].toFloat() / winHeight[0]
            io.setDisplayFramebufferScale(scaleX, scaleY)
        }
        if (wantUpdateMonitors) {
            updateMonitors()
        }
        val currentTime = GLFW.glfwGetTime()
        io.deltaTime = if (time > 0.0) (currentTime - time).toFloat() else 1.0f / 60.0f
        time = currentTime
        updateMousePosAndButtons()
        updateMouseCursor()
        updateGamepads()
    }

    /**
     * Method to restore [org.lwjgl.glfw.GLFW] to it's state prior to calling method [ImGuiImplGlfw.init].
     */
    fun dispose() {
        shutdownPlatformInterface()
        try {
            if (callbacksInstalled) {
                GLFW.glfwSetWindowFocusCallback(windowPtr, prevUserCallbackWindowFocus)!!.free()
                GLFW.glfwSetCursorEnterCallback(windowPtr, prevUserCallbackCursorEnter)!!.free()
                GLFW.glfwSetMouseButtonCallback(windowPtr, prevUserCallbackMouseButton)!!.free()
                GLFW.glfwSetScrollCallback(windowPtr, prevUserCallbackScroll)!!.free()
                GLFW.glfwSetKeyCallback(windowPtr, prevUserCallbackKey)!!.free()
                GLFW.glfwSetCharCallback(windowPtr, prevUserCallbackChar)!!.free()
                callbacksInstalled = false
            }
            GLFW.glfwSetMonitorCallback(prevUserCallbackMonitor)!!.free()
        } catch (ignored: NullPointerException) {
            // ignored
        }
        for (i in 0 until ImGuiMouseCursor.COUNT) {
            GLFW.glfwDestroyCursor(mouseCursors[i])
        }
    }

    private fun detectGlfwVersionAndEnabledFeatures() {
        val major = IntArray(1)
        val minor = IntArray(1)
        val rev = IntArray(1)
        GLFW.glfwGetVersion(major, minor, rev)
        val version = major[0] * 1000 + minor[0] * 100 + rev[0] * 10
        glfwHawWindowTopmost = version >= 3200
        glfwHasWindowAlpha = version >= 3300
        glfwHasPerMonitorDpi = version >= 3300
        glfwHasFocusWindow = version >= 3200
        glfwHasFocusOnShow = version >= 3300
        glfwHasMonitorWorkArea = version >= 3300
    }

    private fun updateMousePosAndButtons() {
        val io = ImGui.getIO()
        for (i in 0 until ImGuiMouseButton.COUNT) {
            // If a mouse press event came, always pass it as "mouse held this frame", so we don't miss click-release events that are shorter than 1 frame.
            io.setMouseDown(i, mouseJustPressed[i] || GLFW.glfwGetMouseButton(windowPtr, i) != 0)
            mouseJustPressed[i] = false
        }
        io.getMousePos(mousePosBackup)
        io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE)
        io.setMouseHoveredViewport(0)
        val platformIO = ImGui.getPlatformIO()
        for (n in 0 until platformIO.viewportsSize) {
            val viewport = platformIO.getViewports(n)
            val windowPtr = viewport.platformHandle
            val focused = GLFW.glfwGetWindowAttrib(windowPtr, GLFW.GLFW_FOCUSED) != 0
            val mouseWindowPtr = if (mouseWindowPtr == windowPtr || focused) windowPtr else 0

            // Update mouse buttons
            if (focused) {
                for (i in 0 until ImGuiMouseButton.COUNT) {
                    io.setMouseDown(i, GLFW.glfwGetMouseButton(windowPtr, i) != 0)
                }
            }

            // Set OS mouse position from Dear ImGui if requested (rarely used, only when ImGuiConfigFlags_NavEnableSetMousePos is enabled by user)
            // (When multi-viewports are enabled, all Dear ImGui positions are same as OS positions)
            if (io.wantSetMousePos && focused) {
                GLFW.glfwSetCursorPos(
                    windowPtr,
                    (mousePosBackup.x - viewport.posX).toDouble(),
                    (mousePosBackup.y - viewport.posY).toDouble()
                )
            }

            // Set Dear ImGui mouse position from OS position
            if (mouseWindowPtr != 0L) {
                GLFW.glfwGetCursorPos(mouseWindowPtr, mouseX, mouseY)
                if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                    // Multi-viewport mode: mouse position in OS absolute coordinates (io.MousePos is (0,0) when the mouse is on the upper-left of the primary monitor)
                    GLFW.glfwGetWindowPos(windowPtr, windowX, windowY)
                    io.setMousePos(mouseX[0].toFloat() + windowX[0], mouseY[0].toFloat() + windowY[0])
                } else {
                    // Single viewport mode: mouse position in client window coordinates (io.MousePos is (0,0) when the mouse is on the upper-left corner of the app window)
                    io.setMousePos(mouseX[0].toFloat(), mouseY[0].toFloat())
                }
            }
        }
    }

    private fun updateMouseCursor() {
        val io = ImGui.getIO()
        val noCursorChange = io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange)
        val cursorDisabled = GLFW.glfwGetInputMode(windowPtr, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED
        if (noCursorChange || cursorDisabled) {
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
                    if (mouseCursors[imguiCursor] != 0L) mouseCursors[imguiCursor] else mouseCursors[ImGuiMouseCursor.Arrow]
                )
                GLFW.glfwSetInputMode(windowPtr, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            }
        }
    }

    private fun updateGamepads() {
        val io = ImGui.getIO()
        if (!io.hasConfigFlags(ImGuiConfigFlags.NavEnableGamepad)) {
            return
        }
        io.setNavInputs(emptyNavInputs)
        val buttons = GLFW.glfwGetJoystickButtons(GLFW.GLFW_JOYSTICK_1)
        val buttonsCount = buttons!!.limit()
        val axis = GLFW.glfwGetJoystickAxes(GLFW.GLFW_JOYSTICK_1)
        val axisCount = axis!!.limit()
        mapButton(ImGuiNavInput.Activate, 0, buttons, buttonsCount, io) // Cross / A
        mapButton(ImGuiNavInput.Cancel, 1, buttons, buttonsCount, io) // Circle / B
        mapButton(ImGuiNavInput.Menu, 2, buttons, buttonsCount, io) // Square / X
        mapButton(ImGuiNavInput.Input, 3, buttons, buttonsCount, io) // Triangle / Y
        mapButton(ImGuiNavInput.DpadLeft, 13, buttons, buttonsCount, io) // D-Pad Left
        mapButton(ImGuiNavInput.DpadRight, 11, buttons, buttonsCount, io) // D-Pad Right
        mapButton(ImGuiNavInput.DpadUp, 10, buttons, buttonsCount, io) // D-Pad Up
        mapButton(ImGuiNavInput.DpadDown, 12, buttons, buttonsCount, io) // D-Pad Down
        mapButton(ImGuiNavInput.FocusPrev, 4, buttons, buttonsCount, io) // L1 / LB
        mapButton(ImGuiNavInput.FocusNext, 5, buttons, buttonsCount, io) // R1 / RB
        mapButton(ImGuiNavInput.TweakSlow, 4, buttons, buttonsCount, io) // L1 / LB
        mapButton(ImGuiNavInput.TweakFast, 5, buttons, buttonsCount, io) // R1 / RB
        mapAnalog(ImGuiNavInput.LStickLeft, 0, -0.3f, -0.9f, axis, axisCount, io)
        mapAnalog(ImGuiNavInput.LStickRight, 0, +0.3f, +0.9f, axis, axisCount, io)
        mapAnalog(ImGuiNavInput.LStickUp, 1, +0.3f, +0.9f, axis, axisCount, io)
        mapAnalog(ImGuiNavInput.LStickDown, 1, -0.3f, -0.9f, axis, axisCount, io)
        if (axisCount > 0 && buttonsCount > 0) {
            io.addBackendFlags(ImGuiBackendFlags.HasGamepad)
        } else {
            io.removeBackendFlags(ImGuiBackendFlags.HasGamepad)
        }
    }

    private fun mapButton(navNo: Int, buttonNo: Int, buttons: ByteBuffer?, buttonsCount: Int, io: ImGuiIO) {
        if (buttonsCount > buttonNo && buttons!![buttonNo].toInt() == GLFW.GLFW_PRESS) {
            io.setNavInputs(navNo, 1.0f)
        }
    }

    private fun mapAnalog(
        navNo: Int,
        axisNo: Int,
        v0: Float,
        v1: Float,
        axis: FloatBuffer?,
        axisCount: Int,
        io: ImGuiIO
    ) {
        var v = if (axisCount > axisNo) axis!![axisNo] else v0
        v = (v - v0) / (v1 - v0)
        if (v > 1.0f) {
            v = 1.0f
        }
        if (io.getNavInputs(navNo) < v) {
            io.setNavInputs(navNo, v)
        }
    }

    private fun updateMonitors() {
        val platformIO = ImGui.getPlatformIO()
        val monitors = GLFW.glfwGetMonitors()
        platformIO.resizeMonitors(0)
        for (n in 0 until monitors!!.limit()) {
            val monitor = monitors[n]
            GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY)
            val vidMode = GLFW.glfwGetVideoMode(monitor)
            val mainPosX = monitorX[0].toFloat()
            val mainPosY = monitorY[0].toFloat()
            val mainSizeX = vidMode!!.width().toFloat()
            val mainSizeY = vidMode.height().toFloat()
            if (glfwHasMonitorWorkArea) {
                GLFW.glfwGetMonitorWorkarea(
                    monitor,
                    monitorWorkAreaX,
                    monitorWorkAreaY,
                    monitorWorkAreaWidth,
                    monitorWorkAreaHeight
                )
            }
            var workPosX = 0f
            var workPosY = 0f
            var workSizeX = 0f
            var workSizeY = 0f

            // Workaround a small GLFW issue reporting zero on monitor changes: https://github.com/glfw/glfw/pull/1761
            if (glfwHasMonitorWorkArea && monitorWorkAreaWidth[0] > 0 && monitorWorkAreaHeight[0] > 0) {
                workPosX = monitorWorkAreaX[0].toFloat()
                workPosY = monitorWorkAreaY[0].toFloat()
                workSizeX = monitorWorkAreaWidth[0].toFloat()
                workSizeY = monitorWorkAreaHeight[0].toFloat()
            }

            // Warning: the validity of monitor DPI information on Windows depends on the application DPI awareness settings,
            // which generally needs to be set in the manifest or at runtime.
            if (glfwHasPerMonitorDpi) {
                GLFW.glfwGetMonitorContentScale(monitor, monitorContentScaleX, monitorContentScaleY)
            }
            val dpiScale = monitorContentScaleX[0]
            platformIO.pushMonitors(
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
        wantUpdateMonitors = false
    }

    //--------------------------------------------------------------------------------------------------------
    // MULTI-VIEWPORT / PLATFORM INTERFACE SUPPORT
    // This is an _advanced_ and _optional_ feature, allowing the back-end to create and handle multiple viewports simultaneously.
    // If you are new to dear imgui or creating a new binding for dear imgui, it is recommended that you completely ignore this section first..
    //--------------------------------------------------------------------------------------------------------
    private fun windowCloseCallback(windowId: Long) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        vp.platformRequestClose = true
    }

    // GLFW may dispatch window pos/size events after calling glfwSetWindowPos()/glfwSetWindowSize().
    // However: depending on the platform the callback may be invoked at different time:
    // - on Windows it appears to be called within the glfwSetWindowPos()/glfwSetWindowSize() call
    // - on Linux it is queued and invoked during glfwPollEvents()
    // Because the event doesn't always fire on glfwSetWindowXXX() we use a frame counter tag to only
    // ignore recent glfwSetWindowXXX() calls.
    private fun windowPosCallback(windowId: Long, xPos: Int, yPos: Int) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        val data = vp.platformUserData as ImGuiViewportDataGlfw
        val ignoreEvent = ImGui.getFrameCount() <= data.ignoreWindowPosEventFrame + 1
        if (ignoreEvent) {
            return
        }
        vp.platformRequestMove = true
    }

    private fun windowSizeCallback(windowId: Long, width: Int, height: Int) {
        val vp = ImGui.findViewportByPlatformHandle(windowId)
        val data = vp.platformUserData as ImGuiViewportDataGlfw
        val ignoreEvent = ImGui.getFrameCount() <= data.ignoreWindowSizeEventFrame + 1
        if (ignoreEvent) {
            return
        }
        vp.platformRequestResize = true
    }

    private inner class CreateWindowFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val data = ImGuiViewportDataGlfw()
            vp.platformUserData = data

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
            data.window =
                GLFW.glfwCreateWindow(vp.sizeX.toInt(), vp.sizeY.toInt(), "No Title Yet", MemoryUtil.NULL, windowPtr)
            data.windowOwned = true
            vp.platformHandle = data.window
            if (IS_WINDOWS) {
                vp.platformHandleRaw = GLFWNativeWin32.glfwGetWin32Window(data.window)
            }
            GLFW.glfwSetWindowPos(data.window, vp.posX.toInt(), vp.posY.toInt())

            // Install GLFW callbacks for secondary viewports
            GLFW.glfwSetMouseButtonCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::mouseButtonCallback)
            GLFW.glfwSetScrollCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::scrollCallback)
            GLFW.glfwSetKeyCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::keyCallback)
            GLFW.glfwSetCharCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::charCallback)
            GLFW.glfwSetWindowCloseCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::windowCloseCallback)
            GLFW.glfwSetWindowPosCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::windowPosCallback)
            GLFW.glfwSetWindowSizeCallback(data.window, this@ImGuiImplGlfwFrameBufferAware::windowSizeCallback)
            GLFW.glfwMakeContextCurrent(data.window)
            GLFW.glfwSwapInterval(0)
        }
    }

    private inner class DestroyWindowFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            if (data != null && data.windowOwned) {
                // Release any keys that were pressed in the window being destroyed and are still held down,
                // because we will not receive any release events after window is destroyed.
                for (i in keyOwnerWindows.indices) {
                    if (keyOwnerWindows[i] == data.window) {
                        keyCallback(
                            data.window,
                            i,
                            0,
                            GLFW.GLFW_RELEASE,
                            0
                        ) // Later params are only used for main viewport, on which this function is never called.
                    }
                }
                Callbacks.glfwFreeCallbacks(data.window)
                GLFW.glfwDestroyWindow(data.window)
            }
            vp.platformUserData = null
            vp.platformHandle = 0
        }
    }

    private class ShowWindowFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            if (IS_WINDOWS && vp.hasFlags(ImGuiViewportFlags.NoTaskBarIcon)) {
                ImGuiImplGlfwNative.win32hideFromTaskBar(vp.platformHandleRaw)
            }
            GLFW.glfwShowWindow(data.window)
        }
    }

    private class GetWindowPosFunction : ImPlatformFuncViewportSuppImVec2() {
        private val posX = IntArray(1)
        private val posY = IntArray(1)
        override fun get(vp: ImGuiViewport, dstImVec2: ImVec2) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            GLFW.glfwGetWindowPos(data.window, posX, posY)
            dstImVec2.x = posX[0].toFloat()
            dstImVec2.y = posY[0].toFloat()
        }
    }

    private class SetWindowPosFunction : ImPlatformFuncViewportImVec2() {
        override fun accept(vp: ImGuiViewport, imVec2: ImVec2) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            data.ignoreWindowPosEventFrame = ImGui.getFrameCount()
            GLFW.glfwSetWindowPos(data.window, imVec2.x.toInt(), imVec2.y.toInt())
        }
    }

    private class GetWindowSizeFunction : ImPlatformFuncViewportSuppImVec2() {
        private val width = IntArray(1)
        private val height = IntArray(1)
        override fun get(vp: ImGuiViewport, dstImVec2: ImVec2) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            GLFW.glfwGetWindowSize(data.window, width, height)
            dstImVec2.x = width[0].toFloat()
            dstImVec2.y = height[0].toFloat()
        }
    }

    private inner class SetWindowSizeFunction : ImPlatformFuncViewportImVec2() {
        private val x = IntArray(1)
        private val y = IntArray(1)
        private val width = IntArray(1)
        private val height = IntArray(1)
        override fun accept(vp: ImGuiViewport, imVec2: ImVec2) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            // Native OS windows are positioned from the bottom-left corner on macOS, whereas on other platforms they are
            // positioned from the upper-left corner. GLFW makes an effort to convert macOS style coordinates, however it
            // doesn't handle it when changing size. We are manually moving the window in order for changes of size to be based
            // on the upper-left corner.
            if (IS_APPLE && !glfwHasOsxWindowPosFix) {
                GLFW.glfwGetWindowPos(data.window, x, y)
                GLFW.glfwGetWindowSize(data.window, width, height)
                GLFW.glfwSetWindowPos(data.window, x[0], y[0] - height[0] + imVec2.y.toInt())
            }
            data.ignoreWindowSizeEventFrame = ImGui.getFrameCount()
            GLFW.glfwSetWindowSize(data.window, imVec2.x.toInt(), imVec2.y.toInt())
        }
    }

    private class SetWindowTitleFunction : ImPlatformFuncViewportString() {
        override fun accept(vp: ImGuiViewport, str: String) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            GLFW.glfwSetWindowTitle(data.window, str)
        }
    }

    private inner class SetWindowFocusFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            if (glfwHasFocusWindow) {
                val data = vp.platformUserData as ImGuiViewportDataGlfw
                GLFW.glfwFocusWindow(data.window)
            }
        }
    }

    private class GetWindowFocusFunction : ImPlatformFuncViewportSuppBoolean() {
        override fun get(vp: ImGuiViewport): Boolean {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            return GLFW.glfwGetWindowAttrib(data.window, GLFW.GLFW_FOCUSED) != 0
        }
    }

    private class GetWindowMinimizedFunction : ImPlatformFuncViewportSuppBoolean() {
        override fun get(vp: ImGuiViewport): Boolean {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            return GLFW.glfwGetWindowAttrib(data.window, GLFW.GLFW_ICONIFIED) != 0
        }
    }

    private inner class SetWindowAlphaFunction : ImPlatformFuncViewportFloat() {
        override fun accept(vp: ImGuiViewport, f: Float) {
            if (glfwHasWindowAlpha) {
                val data = vp.platformUserData as ImGuiViewportDataGlfw
                GLFW.glfwSetWindowOpacity(data.window, f)
            }
        }
    }

    private class RenderWindowFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            GLFW.glfwMakeContextCurrent(data.window)
        }
    }

    private class SwapBuffersFunction : ImPlatformFuncViewport() {
        override fun accept(vp: ImGuiViewport) {
            val data = vp.platformUserData as ImGuiViewportDataGlfw
            GLFW.glfwMakeContextCurrent(data.window)
            GLFW.glfwSwapBuffers(data.window)
        }
    }

    private fun initPlatformInterface() {
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
        val data = ImGuiViewportDataGlfw()
        data.window = windowPtr
        data.windowOwned = false
        mainViewport.platformUserData = data
    }

    private fun shutdownPlatformInterface() {}
    private class ImGuiViewportDataGlfw {
        var window: Long = 0
        var windowOwned = false
        var ignoreWindowPosEventFrame = -1
        var ignoreWindowSizeEventFrame = -1
    }

    companion object {
        private val OS = System.getProperty("os.name", "generic").lowercase(Locale.getDefault())
        protected val IS_WINDOWS = OS.contains("win")
        protected val IS_APPLE = OS.contains("mac") || OS.contains("darwin")
    }
}
