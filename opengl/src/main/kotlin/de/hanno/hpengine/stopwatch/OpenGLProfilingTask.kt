package de.hanno.hpengine.stopwatch


import de.hanno.hpengine.graphics.profiling.ProfilingTask
import de.hanno.hpengine.graphics.profiling.Record
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL33.glGetQueryObjectui64

class OpenGLProfilingTask(
    val name: String,
    val parent: OpenGLProfilingTask? = null,
    private val openGLGPUProfiler: OpenGLGPUProfiler
): ProfilingTask {

    init {
        parent?.children?.add(this)
    }

    val children = ArrayList<OpenGLProfilingTask>()

    private var startQuery: Int = openGLGPUProfiler.genQuery()
    private var endQuery: Int = openGLGPUProfiler.genQuery()

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

    override fun end() {
        endQuery = openGLGPUProfiler.genQuery()
        endTimeCpu = System.nanoTime()
        openGLGPUProfiler.currentTask = parent ?: openGLGPUProfiler.currentTask

        if(parent == null) {
            val last100 = openGLGPUProfiler.collectedTimes.takeLast(100)
            openGLGPUProfiler.collectedTimes.clear()
            openGLGPUProfiler.collectedTimes.addAll(last100)
            saveRecordsHelper()
        }
    }
    private fun saveRecordsHelper() {
        openGLGPUProfiler.collectedTimes.add(Record(name, timeTaken, timeTakenCpu))
        children.forEach {
            it.saveRecordsHelper()
        }
    }

    fun resultsAvailable(): Boolean {
        return glGetQueryObjectui(endQuery, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE
    }
    fun dumpTimings(): String = if (openGLGPUProfiler.profiling) {
        val builder = StringBuilder()
        openGLGPUProfiler.run { dump(builder) }
        builder.toString()
    } else ""
}
