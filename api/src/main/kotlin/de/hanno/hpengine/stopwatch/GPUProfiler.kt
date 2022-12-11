package de.hanno.hpengine.stopwatch

interface GPUProfiler {
    fun dump()
    var currentTimings: String
    var currentAverages: String
    fun start(name: String): ProfilingTask?
}

