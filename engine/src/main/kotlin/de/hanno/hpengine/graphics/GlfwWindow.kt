package de.hanno.hpengine.graphics

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.FrontBufferTarget
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWWindowCloseCallbackI
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GLUtil
import kotlin.system.exitProcess


// Don't make this a local field, we need a strong reference
private val printErrorCallback = GLFWErrorCallbackI { _: Int, _: Long ->
    GLFWErrorCallback.createPrint(System.err)
}

// Don't make this a local field, we need a strong reference
private val exitOnCloseCallback = GLFWWindowCloseCallbackI { _: Long ->
    exitProcess(0)
}

class GlfwWindow @JvmOverloads constructor(
    override var width: Int,
    override var height: Int,
    title: String,
    private var _vSync: Boolean = true,
    errorCallback: GLFWErrorCallbackI = printErrorCallback,
    closeCallback: GLFWWindowCloseCallbackI = exitOnCloseCallback,
    val executor: OpenGlExecutor = OpenGlExecutorImpl()
) : Window<OpenGl>, OpenGlExecutor by executor {

    override var vSync: Boolean
        get() { return _vSync }
        set(value) {
            glfwSwapInterval(if (value) 1 else 0)
            _vSync = value
        }
    override var title = title
        set(value) {
            glfwSetWindowTitle(handle, value)
            field = value
        }
    override val frontBuffer: FrontBufferTarget

    constructor(config: Config) : this(config.width, config.height, "HPEngine", config.performance.isVsync)

    override fun pollEvents() = glfwPollEvents()
    override fun pollEventsInLoop() {
        while (!glfwWindowShouldClose(handle)) {
            pollEvents()
        }
    }

    // Don't remove this strong reference
    private var framebufferSizeCallback: GLFWFramebufferSizeCallback = object : GLFWFramebufferSizeCallback() {
        override fun invoke(windowHandle: Long, width: Int, height: Int) {
            try {
                this@GlfwWindow.width = width
                this@GlfwWindow.height = height
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    override val handle: Long

    init {
        check(glfwInit()) { "Unable to initialize GLFW" }
        glfwSetErrorCallback(errorCallback)
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_RESIZABLE, GL11.GL_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE)
        handle = glfwCreateWindow(width, height, title, 0, 0)
        if (handle == 0L) {
            throw RuntimeException("Failed to create windowHandle")
        }
        setCallbacks(framebufferSizeCallback, closeCallback)

        // This has to happen on the main thread, or it will break, look at glfwShowWindow documentation
        run {
            makeContextCurrent()
            glfwSetInputMode(handle, GLFW_STICKY_KEYS, 1)
            glfwSwapInterval(if (vSync) 1 else 0)
            glfwShowWindow(handle)
            GL.createCapabilities()

            // Don't remove that, or some operating systems won't make context current on another thread
            glfwMakeContextCurrent(0)
        }

        executor.launch {
            makeContextCurrent()
            GL.createCapabilities()
//            GLUtil.setupDebugMessageCallback()
        }

        frontBuffer = createFrontBufferRenderTarget()
        vSync = _vSync
    }

    override fun showWindow() = glfwShowWindow(handle)
    override fun hideWindow() = glfwHideWindow(handle)

    private fun setCallbacks(
        framebufferSizeCallback: GLFWFramebufferSizeCallback,
        closeCallback: GLFWWindowCloseCallbackI
    ) {
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(handle, closeCallback)
    }

    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) = glfwGetCursorPos(handle, mouseX, mouseY)
    override fun getFrameBufferSize(width: IntArray, height: IntArray) = glfwGetFramebufferSize(handle, width, height)
    override fun getKey(keyCode: Int): Int = glfwGetKey(handle, keyCode)
    override fun getMouseButton(buttonCode: Int): Int = glfwGetMouseButton(handle, buttonCode)
    override fun swapBuffers() = glfwSwapBuffers(handle)
    fun makeContextCurrent() = glfwMakeContextCurrent(handle)
    override fun awaitEvents() {
        glfwWaitEvents()
    }

}

fun Window<*>.createFrontBufferRenderTarget(): FrontBufferTarget {
    return object : FrontBufferTarget {
        override val frameBuffer = FrameBuffer.FrontBuffer
        override val name = "FrontBuffer"
        override val clear = Vector4f()

        override var width: Int
            get() = this@createFrontBufferRenderTarget.width
            set(value) {
                this@createFrontBufferRenderTarget.width = value
            }

        override var height: Int
            get() = this@createFrontBufferRenderTarget.height
            set(value) {
                this@createFrontBufferRenderTarget.height = value
            }

        override fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
            gpuContext.bindFrameBuffer(frameBuffer)
            gpuContext.viewPort(0, 0, width, height)
            if (clear) {
                gpuContext.clearDepthAndColorBuffer()
            }
        }
    }
}