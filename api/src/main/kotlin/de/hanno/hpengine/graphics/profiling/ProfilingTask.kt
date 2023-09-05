package de.hanno.hpengine.graphics.profiling

interface ProfilingTask {
    fun end()
    fun dumpTimings(): String
}