package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D

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
    val frontBuffer: RenderTarget<Texture2D>
}