package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWWindowCloseCallbackI
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_FALSE
import java.util.concurrent.Callable
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
                 title: String,
                 override var vSync: Boolean = true,
                 errorCallback: GLFWErrorCallbackI = printErrorCallback,
                 closeCallback: GLFWWindowCloseCallbackI = exitOnCloseCallback): Window<OpenGl>, OpenGlExecutor {

    override var title = title
        set(value) {
            glfwSetWindowTitle(handle, value)
            field = value
        }
    override val frontBuffer: FrontBufferTarget

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

    override val handle: Long
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
        glfwSwapInterval(if(vSync) 1 else 0)

        setCallbacks(framebufferSizeCallback, closeCallback)
        glfwMakeContextCurrent(handle)
        glfwShowWindow(handle)
        frontBuffer = createFrontBufferRenderTarget()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {
        gpuContext.execute("setVSync") {
            glfwSwapInterval(if(vSync) 1 else 0)
            this.vSync = vSync
        }
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

    override fun swapBuffers() {
        glfwSwapBuffers(handle)
    }

    fun makeContextCurrent() {
        glfwMakeContextCurrent(handle)
    }

    val executor = Executor().apply {
        execute("Create Capabilities") {
            makeContextCurrent()
            GL.createCapabilities()
        }
    }
    override val openGLThreadId: Long
        get() = executor.openGLThreadId
    override fun execute(actionName: String, runnable: Runnable, andBlock: Boolean, forceAsync: Boolean) {
        return executor.execute(actionName, runnable, andBlock, forceAsync)
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        return executor.calculate(callable)
    }

    override fun shutdown() = executor.shutdown()

}

fun Window<*>.createFrontBufferRenderTarget(): FrontBufferTarget {
    return object: FrontBufferTarget {
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