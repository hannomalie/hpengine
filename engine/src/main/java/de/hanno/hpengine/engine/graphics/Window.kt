package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.util.commandqueue.CommandQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.AWTException
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.Callable
import javax.swing.JFrame
import javax.swing.SwingUtilities


interface Window<T: BackendType>: OpenGlExecutor {
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
    fun makeContextCurrent()

    val frontBuffer: RenderTarget<Texture2D>
}

val glData = GLData().apply {
    this.majorVersion = 4
    this.minorVersion = 5
    this.debug = true
}

abstract class CustomGlCanvas: AWTGLCanvas(glData) {
    fun isCurrent(): Boolean {
        return platformCanvas.isCurrent(context)
    }

    override fun afterRender() {
        try {
            platformCanvas.unlock()
        } catch (var2: AWTException) {
            throw RuntimeException("Failed to unlock Canvas", var2)
        }
    }

    fun makeContextCurrent() {
        platformCanvas.makeCurrent(context)
    }

    abstract val commandQueue: CommandQueue
}

class AWTWindow: Window<OpenGl>, OpenGlExecutor {
    override var openGLThreadId: Long = -1

    override var handle: Long = 0
        private set

    val frame = JFrame().apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        contentPane.layout = BorderLayout()
        preferredSize = Dimension(600, 600)
    }

    var executeDirectly: () -> Boolean
    val canvas: CustomGlCanvas = object : CustomGlCanvas() {
        init {
            executeDirectly = { Thread.currentThread().id == openGLThreadId && isCurrent() }
        }
        override val commandQueue = object: CommandQueue(executeDirectly = executeDirectly) {
            override fun executeCommands(): Boolean {
                var callable: Callable<*>? = channel.poll()
                while(callable != null) {
                    try {
                        callable.call()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    callable = channel.poll()
                }
                return true
            }
        }
        init {
            preferredSize = Dimension(200, 200)
            frame.contentPane.add(this, BorderLayout.CENTER)
            frame.pack()
            frame.isVisible = true
            frame.transferFocus()
        }
        override fun initGL() {
            createCapabilities()
            handle = context
            openGLThreadId = Thread.currentThread().id
            Thread.currentThread().name  = "OpenGlAWTCanvas"
        }

        override fun paintGL() {

            GL11.glClearColor(0.3f, 0.4f, 0.5f, 1f)
//            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            commandQueue.executeCommands()
            swapBuffers()
        }

    }.apply {
        SwingUtilities.invokeAndWait {
            render()
        }
        val renderLoop = object: Runnable {
            override fun run() {
                try {
                    render()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                SwingUtilities.invokeLater(this);
            }
        }
        SwingUtilities.invokeLater(renderLoop)
    }
    override var title = "XXX"
    override var width: Int
        get() = frame.width
        set(value) { frame.size.width = value }
    override var height: Int
        get() = frame.height
        set(value) { frame.size.height = value }

    override val vSync = false

    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) {

    }

    override fun getFrameBufferSize(width: IntArray, height: IntArray) {
        width[0] = this.width
        height[0] = this.height
    }

    override fun getKey(keyCode: Int): Int {
        return 0
    }

    override fun getMouseButton(buttonCode: Int): Int {
        return 0
    }

    override fun showWindow() {

    }

    override fun hideWindow() {

    }

    override fun pollEvents() {

    }

    override val frontBuffer = object: RenderTarget<Texture2D>(frameBuffer = FrameBuffer.FrontBuffer, name = "FrontBuffer") {
        override var width: Int
            get() {
                return frame.width
            }
            set(value) {
                frame.size.width = value
            }

        override var height: Int
            get() = frame.height
            set(value) {
                frame.size.height = value
            }

        override fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
            super.use(gpuContext, false)
        }
    }

    override fun swapBuffers() {
        canvas.swapBuffers()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {

    }

    override fun execute(actionName: String, runnable: Runnable, andBlock: Boolean, forceAsync: Boolean) {
        canvas.commandQueue.execute(runnable, andBlock, forceAsync)
    }

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return canvas.commandQueue.calculate(Callable<Job> {
            GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
                block()
            }
        })
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        return canvas.commandQueue.calculate(callable)
    }

    override fun makeContextCurrent() {
        canvas.makeContextCurrent()
    }
    override fun shutdown() {
    }

}