package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.profiling.GPUProfiler

context(GPUProfiler)
inline fun <T> profiled(name: String, action: () -> T): T {
    val task = start(name)
    try {
        return action()
    } finally {
        task?.end()
    }
}