package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGlExecutor
import de.hanno.hpengine.engine.graphics.Window
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.awt.GLData
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import org.pushingpixels.substance.api.SubstanceCortex
import org.pushingpixels.substance.api.skin.MarinerSkin
import java.awt.Dimension
import java.util.concurrent.Callable
import javax.swing.JFrame
import javax.swing.SwingUtilities

class AWTEditor: Window<OpenGl>, OpenGlExecutor {
    override var openGLThreadId: Long = -1

    override var handle: Long = 0
        private set
    lateinit var canvas: CustomGlCanvas
    lateinit var frame: RibbonEditor
    init {
        SwingUtilities.invokeAndWait {
            JRibbonFrame.setDefaultLookAndFeelDecorated(true)
            SubstanceCortex.GlobalScope.setSkin(MarinerSkin())
            frame = RibbonEditor()
            frame.preferredSize = Dimension(600,600)
        }
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val glData = GLData().apply {
            majorVersion = 4
            minorVersion = 5
            forwardCompatible = true
//    profile = GLData.Profile.COMPATIBILITY
            samples = 4
            swapInterval = 0
//    this.debug = true
        }

        canvas = object : CustomGlCanvas(glData) {
            override fun initGL() {
                GL.createCapabilities()
                GL11.glClearColor(0.3f, 0.4f, 0.5f, 1f)
                glClear(GL_COLOR_BUFFER_BIT)
                handle = context
                openGLThreadId = Thread.currentThread().id
                println("AWTWindow with OpenGL thread $openGLThreadId")
                Thread.currentThread().name  = "OpenGlAWTCanvas"
            }

            override fun paintGL() {
                commandQueue.executeCommands()
            }

        }.apply {
            isFocusable = true
            frame.addCanvas(this)
            SwingUtilities.invokeAndWait {
                frame.pack()
            }
            frame.isVisible = true
            frame.transferFocus()

            SwingUtilities.invokeAndWait {
                beforeRender()
                unlock()
            }

            val renderLoop = object: Runnable {
                override fun run() {
                    if (!canvas.isValid) return
                    canvas.render()
                    SwingUtilities.invokeLater(this)
                }
            }
            SwingUtilities.invokeLater(renderLoop)
        }
    }

    override var title
        get() = frame.title
        set(value) {
            frame.title = value
        }
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

    override fun showWindow() { }

    override fun hideWindow() { }

    override fun pollEvents() { }

    override val frontBuffer = canvas.createFrontBufferRenderTarget()

    override fun swapBuffers() {
        canvas.swapBuffers()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {
    }

//    override fun execute(actionName: String, runnable: Runnable, andBlock: Boolean, forceAsync: Boolean) {
//        canvas.commandQueue.execute(runnable, andBlock, forceAsync)
//    }
    override fun execute(actionName: String, runnable: Runnable, andBlock: Boolean, forceAsync: Boolean) {
        if(isOpenGLThread && !forceAsync) {
            runnable.run()
            return
        }

        if(andBlock) {
            SwingUtilities.invokeAndWait {
                canvas.beforeRender()
                runnable.run()
                canvas.afterRender()
            }
        } else {
            SwingUtilities.invokeLater {
                canvas.beforeRender()
                runnable.run()
                canvas.afterRender()
            }
        }
    }

//    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
//        return canvas.commandQueue.calculate(callable)
//    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) return callable.call()

        var result: RETURN_TYPE? = null
        SwingUtilities.invokeAndWait {
            canvas.beforeRender()
            result = callable.call()
            canvas.afterRender()
        }
        return result!!
    }

    override fun shutdown() {
    }

    fun init(engine: EngineImpl, config: ConfigImpl) {
        frame.setEngine(engine, config)
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