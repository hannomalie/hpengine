package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGlExecutor
import de.hanno.hpengine.engine.graphics.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
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

class AWTEditor(val config: ConfigImpl) : Window<OpenGl>, OpenGlExecutor {
    val executor = OpenGlExecutorImpl()
    override var openGLThreadId: Long = -1

    override var handle: Long = 0
        private set
    var canvas: CustomGlCanvas
    lateinit var frame: RibbonEditor

    init {
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")
        SwingUtils.invokeAndWait {
            JRibbonFrame.setDefaultLookAndFeelDecorated(true)
            SubstanceCortex.GlobalScope.setSkin(MarinerSkin())
            frame = RibbonEditor()
            frame.preferredSize = Dimension(config.width, config.height)
        }
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val glData = GLData().apply {
            majorVersion = 4
            minorVersion = 5
            forwardCompatible = true
//            samples = 4
            swapInterval = if (config.performance.isVsync) 1 else 0
//            debug = true
        }

        canvas = object : CustomGlCanvas(glData) {
            override fun initGL() {
                GL.createCapabilities()
                println("OpenGL thread id: ${Thread.currentThread().id}")
                println("OpenGL thread former name: ${Thread.currentThread().name}")
                println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION))
                handle = context
                openGLThreadId = Thread.currentThread().id
                println("AWTWindow with OpenGL thread $openGLThreadId")
                Thread.currentThread().name = "OpenGlAWTCanvas"
            }

            override fun paintGL() {
                commandQueue.executeCommands()
            }

        }.apply {
            isFocusable = true
            frame.init(this)
            SwingUtils.invokeAndWait {
                frame.pack()
            }
            frame.isVisible = true
            frame.transferFocus()

            runBlocking(executor.coroutineContext) {
                createContext()
                makeCurrent()
                init()
            }
        }
    }

    override var title
        get() = frame.title
        set(value) {
            frame.title = value
        }
    override var width: Int
        get() = frame.width
        set(value) {
            frame.size.width = value
        }
    override var height: Int
        get() = frame.height
        set(value) {
            frame.size.height = value
        }

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

    override fun showWindow() {}

    override fun hideWindow() {}

    override fun pollEvents() {}

    override val frontBuffer = canvas.createFrontBufferRenderTarget()

    override fun swapBuffers() {
        canvas.swapBuffers()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {
    }

    override suspend fun <T> execute(block: () -> T): T {
        return withContext(executor.coroutineContext) {
            withLockedCanvas {
                block()
            }
        }
    }

    override fun execute(runnable: Runnable) {
        if(executor.isOpenGLThread) return runnable.run()

        executor.execute {
            withLockedCanvas {
                runnable.run()
            }
        }
    }

    private inline fun <T> withLockedCanvas(block: () -> T): T = try {
        canvas.beforeRender()
        block()
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    } finally {
        canvas.afterRender()
    }

    override fun <RETURN_TYPE> calculateX(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(executor.isOpenGLThread) return callable.call()

        return executor.calculate {
            withLockedCanvas {
                callable.call()
            }
        }
    }

    override fun shutdown() {
    }

    fun init(engine: EngineImpl, config: ConfigImpl) {
        frame.setEngine(engine, config)
    }
}