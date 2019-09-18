package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWWindowCloseCallbackI
import org.lwjgl.opengl.GL11
import kotlin.system.exitProcess

//     Don't make this a local field, we need a strong reference
private val printErrorCallback = GLFWErrorCallbackI { error: Int, description: Long ->
    GLFWErrorCallback.createPrint(System.err)
}

private val exitOnCloseCallback = GLFWWindowCloseCallbackI { l: Long ->
    exitProcess(0)
}

class GlfwWindow(override var width: Int,
                 override var height: Int,
                 override val title: String,
                 errorCallback: GLFWErrorCallbackI = printErrorCallback,
                 closeCallback: GLFWWindowCloseCallbackI = exitOnCloseCallback): Window<OpenGl> {

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
        GLFW.glfwSetErrorCallback(errorCallback)
        GLFW.glfwInit()
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        handle = GLFW.glfwCreateWindow(width, height, title, 0, 0)
        if (handle == 0L) {
            throw RuntimeException("Failed to create windowHandle")
        }

        GLFW.glfwMakeContextCurrent(handle)

        GLFW.glfwSetInputMode(handle, GLFW.GLFW_STICKY_KEYS, 1)
        GLFW.glfwSwapInterval(1)
//        glfwWindowHint(GLFW_VISIBLE, GL_FALSE)

        setCallbacks(framebufferSizeCallback, closeCallback)
        GLFW.glfwShowWindow(handle)
    }

    private fun setCallbacks(framebufferSizeCallback: GLFWFramebufferSizeCallback, closeCallback: GLFWWindowCloseCallbackI) {
        GLFW.glfwMakeContextCurrent(handle)
        GLFW.glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(handle, closeCallback)
    }
    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) {
        GLFW.glfwGetCursorPos(handle, mouseX, mouseY)
    }
    override fun getFrameBufferSize(width: IntArray, height: IntArray) {
        GLFW.glfwGetFramebufferSize(handle, width, height)
    }
    override fun getKey(keyCode: Int): Int {
        return GLFW.glfwGetKey(handle, keyCode)
    }
    override fun getMouseButton(buttonCode: Int): Int {
        return GLFW.glfwGetMouseButton(handle, buttonCode)
    }
}