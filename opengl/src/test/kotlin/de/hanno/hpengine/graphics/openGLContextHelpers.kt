package de.hanno.hpengine.graphics

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler


fun createOpenGLContext(
    config: Config = Config(),
): Pair<GlfwWindow, OpenGLContext> {
    val profiler = OpenGLGPUProfiler(config.debug::profiling)
    val window = GlfwWindow(
        config,
        profiler,
        visible = false,
    )
    return Pair(
        window, OpenGLContext(
            window,
            config
        )
    )
}
