package de.hanno.hpengine.graphics.profiling

interface GPUProfiler {
    fun dump()
    var currentTimings: String
    var currentAverages: String
    fun start(name: String): ProfilingTask?
}

