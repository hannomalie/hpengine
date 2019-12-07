package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.AWTException


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
}

