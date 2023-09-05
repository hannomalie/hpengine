package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.profiling.ProfilingTask

context(GraphicsApi)
inline fun <T> profiled(name: String, action: () -> T): T {
    val task = onGpu { profiler.start(name) }
    try {
        return action()
    } finally {
        task?.end()
    }
}
context(GPUProfiler, GpuExecutor)
inline fun <T> profiledFoo(name: String, action: () -> T): ProfilingTask? = invoke { start(name) }.also { task ->
    try {
        action()
    } finally {
        task?.end()
    }
}