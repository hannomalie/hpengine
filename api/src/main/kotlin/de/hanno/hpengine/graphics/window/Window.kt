package de.hanno.hpengine.graphics.window

import de.hanno.hpengine.graphics.GpuExecutor
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.rendertarget.FrontBufferTarget


interface Window {
    val gpuExecutor: GpuExecutor
    val profiler: GPUProfiler
    val handle: Long
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
    fun shouldClose(): Boolean
    fun closeIfReqeusted()
}
