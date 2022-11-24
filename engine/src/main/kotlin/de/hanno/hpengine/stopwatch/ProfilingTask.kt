package de.hanno.hpengine.stopwatch

import de.hanno.hpengine.stopwatch.GPUProfiler.currentTask
import de.hanno.hpengine.stopwatch.GPUProfiler.dump

import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE
import org.lwjgl.opengl.GL15.glGetQueryObjectui
import org.lwjgl.opengl.GL33.glGetQueryObjectui64

import java.util.ArrayList

class ProfilingTask(val name: String, val parent: ProfilingTask? = null) {

    init {
        parent?.children?.add(this)
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

        if(parent == null) {
            val last100 = GPUProfiler.collectedTimes.takeLast(100)
            GPUProfiler.collectedTimes.clear()
            GPUProfiler.collectedTimes.addAll(last100)
            saveRecordsHelper()
        }
    }
    private fun saveRecordsHelper() {
        GPUProfiler.collectedTimes.add(GPUProfiler.Record(name, timeTaken, timeTakenCpu))
        children.forEach {
            it.saveRecordsHelper()
        }
    }

    fun resultsAvailable(): Boolean {
        return glGetQueryObjectui(endQuery, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE
    }
    fun dumpTimings(): String {
        if (GPUProfiler.profiling) {
            val builder = StringBuilder()
            dump(builder)
            return builder.toString()
        }
        return ""
    }
}
