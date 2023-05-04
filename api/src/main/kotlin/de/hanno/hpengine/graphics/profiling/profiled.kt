package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.profiling.GPUProfiler

context(GPUProfiler, GraphicsApi)
inline fun <T> profiled(name: String, action: () -> T): T {
    val task = onGpu { start(name) }
    try {
        return action()
    } finally {
        task?.end()
    }
}
context(GPUProfiler, GpuExecutor)
inline fun <T> profiledFoo(name: String, action: () -> T): T {
    val task = invoke { start(name) }
    try {
        return action()
    } finally {
        task?.end()
    }
}