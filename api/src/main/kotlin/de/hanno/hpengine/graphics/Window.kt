package de.hanno.hpengine.graphics

import de.hanno.hpengine.backend.BackendType
import de.hanno.hpengine.graphics.renderer.rendertarget.FrontBufferTarget


interface Window<T : BackendType>: GpuExecutor {
    var title: String

    var width: Int
    var height: Int

    var vSync: Boolean

    fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray)
    fun getFrameBufferSize(width: IntArray, height: IntArray)
    fun getKey(keyCode: Int): Int
    fun getMouseButton(buttonCode: Int): Int
    fun showWindow()
    fun hideWindow()
    fun pollEvents()
    fun pollEventsInLoop()
    fun swapBuffers()

    fun awaitEvents()

    val frontBuffer: FrontBufferTarget
}
