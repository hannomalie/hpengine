package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.util.commandqueue.CommandQueue
import org.joml.Vector4f
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.AWTException
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

    val frontBuffer: FrontBufferTarget
}

abstract class CustomGlCanvas(glData: GLData): AWTGLCanvas(glData) {
    val commandQueue = CommandQueue { SwingUtilities.isEventDispatchThread() }

    fun init() {
        if (!initCalled) {
            initGL()
            initCalled = true
        }
    }

    fun makeCurrent() {
        platformCanvas.makeCurrent(context)
    }
    fun isCurrent() = platformCanvas.isCurrent(context)

    fun createContext() {
        if (context == 0L) {
            try {
                context = platformCanvas.create(this, data, effective)
            } catch (var3: AWTException) {
                throw RuntimeException("Exception while creating the OpenGL context", var3)
            }
        }
    }

    public override fun beforeRender() {
        super.beforeRender()
    }

    public override fun afterRender() {
        super.afterRender()
    }

    fun unlock() {
        platformCanvas.unlock()
    }
    fun lock() {
        platformCanvas.lock()
    }

    fun createFrontBufferRenderTarget(): FrontBufferTarget {
        return object: FrontBufferTarget {
            override val frameBuffer = FrameBuffer.FrontBuffer
            override val name = "FrontBuffer"

            override val width: Int
                get() = this@CustomGlCanvas.width

            override val height: Int
                get() = this@CustomGlCanvas.height

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

