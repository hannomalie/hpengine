package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lwjgl.opengl.GL
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.Callable
import javax.swing.JFrame
import javax.swing.SwingUtilities

class AWTWindow: Window<OpenGl>, OpenGlExecutor {
    override var openGLThreadId: Long = -1

    override var handle: Long = 0
        private set
    lateinit var canvas: CustomGlCanvas
    lateinit var frame: JFrame
    init {
        SwingUtilities.invokeAndWait {
//            JRibbonFrame.setDefaultLookAndFeelDecorated(true)
//            SubstanceCortex.GlobalScope.setSkin(MarinerSkin())
            frame = JFrame().apply {
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                contentPane.layout = BorderLayout()
                preferredSize = Dimension(600, 600)
            }
            canvas = object : CustomGlCanvas() {
                init {
                    preferredSize = Dimension(200, 200)
                    frame.add(this, BorderLayout.CENTER)
                    frame.pack()
                    frame.isVisible = true
                    frame.transferFocus()
                }
                override fun initGL() {
                    GL.createCapabilities()
                    handle = context
                    openGLThreadId = Thread.currentThread().id
                    Thread.currentThread().name  = "OpenGlAWTCanvas"
                }

                override fun paintGL() {
                }

            }.apply {
                SwingUtilities.invokeLater {
                    render()
                }
            }
        }
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
//        return executor.execute(actionName, runnable, andBlock, forceAsync)
        if(isOpenGLThread && !forceAsync) return runnable.run()

        if(andBlock) {
            SwingUtilities.invokeAndWait { runnable.run() }
        } else {
            SwingUtilities.invokeLater(runnable)
        }
    }

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
//        return executor.launch(block)
        return calculate(Callable {
            GlobalScope.launch { block() }
        })
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
//        return executor.calculate(callable)
        if(isOpenGLThread) return callable.call()

        var result: RETURN_TYPE? = null
        SwingUtilities.invokeAndWait {
            result = callable.call()
        }
        return result!!
    }

    override fun makeContextCurrent() {
        canvas.makeContextCurrent()
    }
    override fun shutdown() {
    }

    val Thread.isOpenGLThread: Boolean
        get() {
            return id == openGLThreadId
        }

    val isOpenGLThread: Boolean
        get() {
            return Thread.currentThread().isOpenGLThread
        }

}