package de.hanno.hpengine.graphics.window

import de.hanno.hpengine.graphics.GpuExecutor
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.rendertarget.FrontBufferTarget


interface Window: AutoCloseable {
    val gpuExecutor: GpuExecutor
    val profiler: GPUProfiler
    val frontBuffer: FrontBufferTarget
    val handle: Long
    var title: String

    var width: Int
    var height: Int

    var vSync: Boolean

    fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray)
    fun getFrameBufferSize(width: IntArray, height: IntArray)
    fun getKey(keyCode: Int): Int
    fun getMouseButton(buttonCode: Int): Int
    fun show()
    fun hide()
    fun pollEvents()
    fun swapBuffers()

    fun awaitEvents()

    fun closeIfRequested()
    fun setVisible(visible: Boolean)
}
