package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWWindowCloseCallbackI
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_FALSE
import kotlin.system.exitProcess

//     Don't make this a local field, we need a strong reference
private val printErrorCallback = GLFWErrorCallbackI { error: Int, description: Long ->
    GLFWErrorCallback.createPrint(System.err)
}

private val exitOnCloseCallback = GLFWWindowCloseCallbackI { l: Long ->
    exitProcess(0)
}

class GlfwWindow @JvmOverloads constructor(override var width: Int,
                 override var height: Int,
                 override val title: String,
                 errorCallback: GLFWErrorCallbackI = printErrorCallback,
                 closeCallback: GLFWWindowCloseCallbackI = exitOnCloseCallback): Window<OpenGl> {

//     TODO: Avoid this somehow, move to update, but only when update is called before all the
//    contexts and stuff, or the fresh window will get a message dialog that it doesnt respond
    private val pollEventsThread = Thread({
        while(true) {
            pollEvents()
            Thread.sleep(16)
        }
    }, "PollWindowEvents").apply { start() }

    override fun pollEvents() = glfwPollEvents()

    // Don't remove this strong reference
    private var framebufferSizeCallback: GLFWFramebufferSizeCallback = object : GLFWFramebufferSizeCallback() {
        override fun invoke(windowHandle: Long, width: Int, height: Int) {
            try {
                this@GlfwWindow.width = width
                this@GlfwWindow.height = height
            } catch (e: Exception) { e.printStackTrace() }

        }
    }

    val handle: Long
    init {
        glfwSetErrorCallback(errorCallback)
        glfwInit()
        glfwWindowHint(GLFW_RESIZABLE, GL11.GL_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE)
        handle = glfwCreateWindow(width, height, title, 0, 0)
        if (handle == 0L) {
            throw RuntimeException("Failed to create windowHandle")
        }

        glfwMakeContextCurrent(handle)

        glfwSetInputMode(handle, GLFW_STICKY_KEYS, 1)
        glfwSwapInterval(1)

        setCallbacks(framebufferSizeCallback, closeCallback)
        glfwMakeContextCurrent(handle)
        glfwShowWindow(handle)
    }

    override fun showWindow() {
        glfwShowWindow(handle)
    }
    override fun hideWindow() {
        glfwHideWindow(handle)
    }

    private fun setCallbacks(framebufferSizeCallback: GLFWFramebufferSizeCallback, closeCallback: GLFWWindowCloseCallbackI) {
        glfwMakeContextCurrent(handle)
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(handle, closeCallback)
    }
    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) {
        glfwGetCursorPos(handle, mouseX, mouseY)
    }
    override fun getFrameBufferSize(width: IntArray, height: IntArray) {
        glfwGetFramebufferSize(handle, width, height)
    }
    override fun getKey(keyCode: Int): Int {
        return glfwGetKey(handle, keyCode)
    }
    override fun getMouseButton(buttonCode: Int): Int {
        return glfwGetMouseButton(handle, buttonCode)
    }
}