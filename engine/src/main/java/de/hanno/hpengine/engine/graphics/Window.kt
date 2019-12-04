package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame


interface Window<T: BackendType> {
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
}

abstract class CustomGlCanvas: AWTGLCanvas(glData) {
    fun makeContextCurrent() {
        platformCanvas.makeCurrent(context)
    }
}

class AWTWindow: Window<OpenGl> {
    override var handle: Long = 0
        private set

    val frame = JFrame().apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        contentPane.layout = BorderLayout()
    }

    val canvas = object : CustomGlCanvas() {
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
        }

        override fun paintGL() { }

        override fun repaint() { }
    }.apply {
        render()
    }
    override var title = "XXX"
    override var width = frame.width
    override var height = frame.height

    override val vSync =false

    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) {

    }

    override fun getFrameBufferSize(width: IntArray, height: IntArray) {
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

    override val frontBuffer: RenderTarget<Texture2D>
        get() = object: RenderTarget<Texture2D>(frameBuffer = FrameBuffer.FrontBuffer, name = "FrontBuffer") {
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

            override fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
                super.use(gpuContext, false)
            }
        }

    override fun swapBuffers() {
        canvas.swapBuffers()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {

    }

    override fun makeContextCurrent() {
        canvas.makeContextCurrent()
    }

}