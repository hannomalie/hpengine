package de.hanno.hpengine.graphics


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.GLU
import de.hanno.hpengine.graphics.renderer.rendertarget.OpenGLFrameBuffer
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
import org.lwjgl.system.APIUtil
import java.lang.reflect.Field
import kotlin.system.exitProcess


// Don't make this a local field, we need a strong reference
private val printErrorCallback = GLFWErrorCallbackI { _: Int, _: Long ->
    GLFWErrorCallback.createPrint(System.err)
}

// Don't make this a local field, we need a strong reference
private val exitOnCloseCallback = GLFWWindowCloseCallbackI { _: Long ->
    exitProcess(0)
}

private val exceptionOnErrorCallback = object : GLFWErrorCallback() {
    private val ERROR_CODES = APIUtil.apiClassTokens(
        { _: Field?, value: Int -> value in 0x10001..0x1ffff }, null,
        org.lwjgl.glfw.GLFW::class.java
    )

    override fun invoke(error: Int, description: Long) {
        val msg = getDescription(description)
        "[LWJGL] ${ERROR_CODES[error]} error\n" +
        "Error: ${GLU.gluErrorString(error)}" +
        "\tDescription : $msg"

        throw OpenGlException(msg)
    }
}
class OpenGlException(msg: String): RuntimeException(msg)

class GlfwWindow(
    override var width: Int,
    override var height: Int,
    title: String,
    vSync: Boolean = true,
    closeCallback: GLFWWindowCloseCallbackI = exitOnCloseCallback,
    private val executor: GpuExecutor = OpenGlExecutorImpl()
) : Window, GpuExecutor by executor {

    constructor(config: Config) : this(config.width, config.height, "HPEngine", config.performance.isVsync)

    override var vSync: Boolean = vSync
        set(value) {
            executor.invoke {
                glfwSwapInterval(if (value) 1 else 0)
            }
            field = value
        }
    override var title = title
        set(value) {
            glfwSetWindowTitle(handle, value)
            field = value
        }
    override val frontBuffer: FrontBufferTarget

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
        glfwSetErrorCallback(exceptionOnErrorCallback)
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_RESIZABLE, GL11.GL_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE)
        handle = glfwCreateWindow(width, height, title, 0, 0).apply {
            check(this != 0L) { "Failed to create windowHandle" }
        }
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(handle, closeCallback)

        // This has to happen on the main thread, or it will break, look at glfwShowWindow documentation
        makeContextCurrent()
        glfwSetInputMode(handle, GLFW_STICKY_KEYS, 1)
        glfwSwapInterval(if (this.vSync) 1 else 0)
        glfwShowWindow(handle)
        GL.createCapabilities()

        // Don't remove that, or some operating systems won't make context current on another thread
        glfwMakeContextCurrent(0)

        executor.launch {
            makeContextCurrent()
            GL.createCapabilities()
        }

        frontBuffer = createFrontBufferRenderTarget()
    }

    override fun pollEvents() = glfwPollEvents()
    override fun pollEventsInLoop() {
        while (!glfwWindowShouldClose(handle)) {
            pollEvents()
        }
    }

    override fun showWindow() = glfwShowWindow(handle)
    override fun hideWindow() = glfwHideWindow(handle)

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

fun Window.createFrontBufferRenderTarget(): FrontBufferTarget = object : FrontBufferTarget {
    val frameBuffer = OpenGLFrameBuffer.FrontBuffer
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

    context(GraphicsApi)
    override fun use(clear: Boolean) {
        bindFrameBuffer(frameBuffer)
        viewPort(0, 0, width, height)
        if (clear) {
            clearDepthAndColorBuffer()
        }
    }
}