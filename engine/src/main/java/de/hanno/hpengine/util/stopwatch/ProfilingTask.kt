package de.hanno.hpengine.util.stopwatch

import de.hanno.hpengine.util.TypedTuple
import de.hanno.hpengine.util.stopwatch.GPUProfiler.currentTask
import de.hanno.hpengine.util.stopwatch.GPUProfiler.dump

import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE
import org.lwjgl.opengl.GL15.glGetQueryObjectui
import org.lwjgl.opengl.GL33.glGetQueryObjectui64

import java.util.ArrayList
import java.util.HashMap

class ProfilingTask(val name: String, val parent: ProfilingTask? = null) {

    init {
        parent?.let {
            it.children.add(this)
        }
    }

    val children = ArrayList<ProfilingTask>()

    private var startQuery: Int = GPUProfiler.query
    private var endQuery: Int = GPUProfiler.query

    private var startTimeCpu: Long = System.nanoTime()
    private var endTimeCpu: Long = 0

    val startTime: Long
        get() = glGetQueryObjectui64(startQuery, GL_QUERY_RESULT)

    val endTime: Long
        get() = glGetQueryObjectui64(endQuery, GL_QUERY_RESULT)

    val timeTaken: Long
        get() = endTime - startTime

    val timeTakenCpu: Long
        get() = endTimeCpu - startTimeCpu

    fun end() {
        this.endQuery = GPUProfiler.query
        this.endTimeCpu = System.nanoTime()
        currentTask = parent ?: currentTask
        GPUProfiler.collectedTimes.add(GPUProfiler.Record(name, timeTaken, timeTakenCpu))
    }

    fun resultsAvailable(): Boolean {
        return glGetQueryObjectui(endQuery, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE
    }
    fun dumpTimings(): String {
        if (GPUProfiler.PROFILING_ENABLED) {
            val builder = StringBuilder()
            this.dump(builder)
            return builder.toString()
        }
        return ""
    }
}
