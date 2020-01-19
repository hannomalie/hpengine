package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.util.commandqueue.CommandQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.swing.SwingDispatcher
import org.joml.Vector4f
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseWheelEvent
import java.util.concurrent.Callable
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

