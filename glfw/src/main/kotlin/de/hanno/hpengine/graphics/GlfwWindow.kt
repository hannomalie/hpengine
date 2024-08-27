package de.hanno.hpengine.graphics


import GLUtil
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.executors.FrameBasedOpenGLExecutor
import de.hanno.hpengine.graphics.executors.OpenGlException
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.GLU
import de.hanno.hpengine.graphics.rendertarget.FrontBufferTarget
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.lifecycle.Termination
import org.joml.Vector4f
import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL32
import org.lwjgl.system.APIUtil
import org.lwjgl.system.Callback
import java.lang.reflect.Field


// Don't make this a local field, we need a strong reference
private val printErrorCallback = GLFWErrorCallbackI { _: Int, _: Long ->
    GLFWErrorCallback.createPrint(System.err)
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

        throw OpenGlException(msg).apply {
            printStackTrace()
        }
    }
}
class GlfwWindow(
    override var width: Int,
    override var height: Int,
    private val config: Config,
    private val termination: Termination,
    title: String,
    vSync: Boolean = true,
    visible: Boolean,
    override val profiler: GPUProfiler,
    parentWindow: GlfwWindow? = null,
    logLevel: GLUtil.Severity = GLUtil.Severity.NOTIFICATION,
) : Window {
    final override val gpuExecutor: GpuExecutor

    private var debugProc: Callback? = null

    constructor(
        config: Config,
        profiler: GPUProfiler,
        termination: Termination,
        visible: Boolean = true,
    ) : this(
        config.width, config.height, config, termination,
        "HPEngine", config.performance.isVsync,
        visible = visible,
        profiler = profiler
    )

    override var vSync: Boolean = vSync
        set(value) {
            gpuExecutor.invoke {
                glfwSwapInterval(if (value) 1 else 0)
            }
            field = value
        }
    override var title = title
        set(value) {
            glfwSetWindowTitle(handle, value)
            field = value
        }
    final override val frontBuffer: FrontBufferTarget

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

    final override val handle: Long

    init {
        check(glfwInit()) { "Unable to initialize GLFW" }
        glfwSetErrorCallback(exceptionOnErrorCallback)
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, if(visible) GL_TRUE else GL_FALSE)
        handle = glfwCreateWindow(width, height, title, 0, parentWindow?.handle ?: 0).apply {
            check(this != 0L) { "Failed to create windowHandle" }
        }
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback)
//        glfwSetWindowCloseCallback(handle, closeCallback)

        // This has to happen on the main thread, or it will break, look at glfwShowWindow documentation
        makeContextCurrent()
        glfwSetInputMode(handle, GLFW_STICKY_KEYS, 1)
        glfwSwapInterval(if (this.vSync) 1 else 0)
        GL.createCapabilities()
        debugProc = GLUtil.setupDebugMessageCallback(logLevel = logLevel)
        GL32.glViewport(0, 0, Integer.max(width, 0), Integer.max(height, 0))

        // Don't remove that, or some operating systems won't make context current on another thread
        glfwMakeContextCurrent(0)

        gpuExecutor = FrameBasedOpenGLExecutor(profiler, termination) {
                makeContextCurrent()
                GL.createCapabilities()
            }

        frontBuffer = createFrontBufferRenderTarget()
    }

    override fun setVisible(visible: Boolean) {
        if(visible) {
            show()
        } else {
            hide()
        }
    }
    override fun pollEvents() {
        glfwPollEvents()

        if(glfwWindowShouldClose(handle)) {
            termination.terminationRequested.set(true)
        }
    }

    override fun close() {
        debugProc?.free()
        Callbacks.glfwFreeCallbacks(handle)
        glfwDestroyWindow(handle)
        glfwTerminate()
    }
    override fun closeIfRequested() {
        if (termination.terminationRequested.get()) {
            close()
        }
    }

    override fun show() = glfwShowWindow(handle)
    override fun hide() = glfwHideWindow(handle)

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

    override fun use(graphicsApi: GraphicsApi, clear: Boolean) = graphicsApi.run {
        bindFrameBuffer(frameBuffer)
        viewPort(0, 0, width, height)
        if (clear) {
            clearDepthAndColorBuffer()
        }
    }
}