package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.window.Window

interface GpuExecutor {
    val gpuProfiler: GPUProfiler
    suspend fun <T> execute(block: () -> T): T
    operator fun <T> invoke(block: () -> T): T
    fun launch(block: () -> Unit)

    var perFrameAction: (() -> Unit)?
    val backgroundContext: GpuExecutor?
    var parentContext: Window?
}
