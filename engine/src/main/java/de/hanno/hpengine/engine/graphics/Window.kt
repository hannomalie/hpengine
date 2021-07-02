package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrontBufferTarget
import kotlinx.coroutines.runBlocking
import org.joml.Vector4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.AWTException


interface Window<T : BackendType>: OpenGlExecutor {
    val handle: Long // TODO: Remove this, because it is OpenGl/GLFW specific

    var title: String

    var width: Int
    var height: Int

    val vSync: Boolean
    fun setVSync(vSync: Boolean, gpuContext: GpuContext<T>)

    fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray)
    fun getFrameBufferSize(width: IntArray, height: IntArray)
    fun getKey(keyCode: Int): Int
    fun getMouseButton(buttonCode: Int): Int
    fun showWindow()
    fun hideWindow()
    fun pollEvents()
    fun swapBuffers()

    fun awaitEvents()

    val frontBuffer: FrontBufferTarget
}

private fun Config.createGlData(): GLData {
    val glData = GLData().apply {
        majorVersion = 4
        minorVersion = 5
        forwardCompatible = true
//            samples = 4
        swapInterval = if (this@createGlData.performance.isVsync) 1 else 0
//            debug = true
    }
    return glData
}

class CustomGlCanvas(config: Config, val executor: OpenGlExecutorImpl): AWTGLCanvas(config.createGlData()) {
    // Caution, this has to be called when the canvas is attached to a window
    fun init() = runBlocking(executor.coroutineContext) {
        isFocusable = true
        createContext()
        makeCurrent()
        initGL()
    }

    override fun initGL() {
        GL.createCapabilities()
        println("OpenGL thread id: ${Thread.currentThread().id}")
        println("OpenGL thread former name: ${Thread.currentThread().name}")
        println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION))
        handle = context
        Thread.currentThread().name = "OpenGlAWTCanvas"
    }
    override fun paintGL() { }

    fun makeCurrent() {
        canvas.makeCurrent(context)
    }
    fun isCurrent() = canvas.isCurrent(context)

    fun createContext() {
        if (context != 0L) throw IllegalStateException("You tried to create a contex twice, which isnot possible")

        try {
            context = canvas.create(this, data, effective)
        } catch (var3: AWTException) {
            throw RuntimeException("Exception while creating the OpenGL context", var3)
        }
    }

    var handle: Long = -1
        private set

    val canvas
        get() = super.platformCanvas

    fun createFrontBufferRenderTarget(): FrontBufferTarget {
        return object: FrontBufferTarget {
            override val frameBuffer = FrameBuffer.FrontBuffer
            override val name = "FrontBuffer"

            private val scale: Double
                get() {
                    val dotsPerInch = java.awt.Toolkit.getDefaultToolkit().screenResolution
                    return dotsPerInch.toDouble() / 96.toDouble()
                }
            override val width: Int
                get() = (this@CustomGlCanvas.width * scale).toInt()

            override val height: Int
                get() = (this@CustomGlCanvas.height * scale).toInt()

            override fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
                gpuContext.bindFrameBuffer(frameBuffer)
                gpuContext.viewPort(0, 0, width, height)
                if (clear) {
                    gpuContext.clearDepthAndColorBuffer()
                }
            }

            override val clear = Vector4f()
        }
    }
}

