package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.Executor
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGlExecutor
import de.hanno.hpengine.engine.graphics.Window
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JFrame

class AWTEditor(val config: ConfigImpl) : Window<OpenGl>, OpenGlExecutor {
    val executor = Executor(Dispatchers.Swing)
    override var openGLThreadId: Long = -1

    override var handle: Long = 0
        private set
    lateinit var canvas: CustomGlCanvas
    lateinit var frame: RibbonEditor
    init {
        executor.execute {
            JRibbonFrame.setDefaultLookAndFeelDecorated(true)
            SubstanceCortex.GlobalScope.setSkin(MarinerSkin())
            frame = RibbonEditor()
            frame.preferredSize = Dimension(config.width, config.height)
        }.get()
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val glData = GLData().apply {
            majorVersion = 4
            minorVersion = 5
            forwardCompatible = true
//    profile = GLData.Profile.COMPATIBILITY
            samples = 4
            swapInterval = if(config.performance.isVsync) 1 else 0
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
            frame.init(this)
            executor.execute {
                frame.pack()
            }.get()
            frame.isVisible = true
            frame.transferFocus()

            executor.execute {
//                beforeRender()
//                unlock()
                render()
            }.get()

            val renderLoop = object: Runnable {
                override fun run() {
                    if (!canvas.isValid) return
                    canvas.render()
                    executor.execute(this)
//                    SwingUtilities.invokeLater(this)
                }
            }

            executor.execute(renderLoop)
//            SwingUtilities.invokeLater(renderLoop)
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

    override suspend fun <T> execute(block: () -> T): T = withContext(executor.coroutineContext) {
        try {
            canvas.beforeRender()
            block()
        } finally {
            canvas.afterRender()
        }
    }
    override fun execute(runnable: Runnable): Future<Unit> {
        if(isOpenGLThread) {
            return CompletableFuture.completedFuture(runnable.run())
        } else {
            return GlobalScope.future(Dispatchers.Swing, start = if(isOpenGLThread) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
                canvas.beforeRender()
                try {
                    runnable.run()
                } finally {
                    canvas.afterRender()
                }
            }
        }
    }

    private fun <T> withLockedCanvas(block: () -> T): T = try {
        if(isOpenGLThread) {
            canvas.beforeRender()
        }
        block()
    } finally {
        if(isOpenGLThread) {
            canvas.afterRender()
        }
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) return callable.call()

        return GlobalScope.future(Dispatchers.Swing, start = if(isOpenGLThread) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
            canvas.beforeRender()
            callable.call().apply {
                canvas.afterRender()
            }
        }.get()
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