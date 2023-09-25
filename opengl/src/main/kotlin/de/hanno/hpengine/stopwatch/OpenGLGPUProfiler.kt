package de.hanno.hpengine.stopwatch

import de.hanno.hpengine.graphics.profiling.AverageHelper
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.profiling.ProfilingTask
import de.hanno.hpengine.graphics.profiling.Record
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33
import kotlin.reflect.KMutableProperty0

class OpenGLGPUProfiler(private val profilingEnabled: KMutableProperty0<Boolean>): GPUProfiler {
    var profiling: Boolean by profilingEnabled

    private var queryObjects: ArrayList<Int> = ArrayList()

    private var frameCounter: Int = 0

    internal var currentTask: OpenGLProfilingTask? = null

    var collectedTimes: ArrayList<Record> = ArrayList()

    fun genQuery(): Int {
        val query: Int = if (queryObjects.isNotEmpty()) {
            queryObjects.removeAt(queryObjects.size - 1)
        } else {
            GL15.glGenQueries()
        }

        GL33.glQueryCounter(query, GL33.GL_TIMESTAMP)

        return query
    }
    override var currentTimings = ""
    override var currentAverages = ""

    override fun start(name: String): OpenGLProfilingTask? = if (profiling) {
        val newTask = OpenGLProfilingTask(name, currentTask, this)
        currentTask = newTask
        newTask
    } else null

    fun dumpAverages(sampleCount: Int = Integer.MAX_VALUE): String {

        val averages = calculateAverages(sampleCount)

        if (averages.isEmpty()) {
            return ""
        }
        val builder = StringBuilder("")
        builder.append("##########################################\n")
        builder.append("name\t\t\t|  ms(variance)\t|ms cpu(variance)\t|\tsamples\n")
        averages.entries.forEach { s ->
            var name = s.key
            while (name.length < 30) {
                name += " "
            }
            val clippedName = name.substring(0, Math.min(name.length, 30))
            val meanTimeGpu = s.value.summedTime / s.value.count
            val meanTimeCpu = s.value.summedTimeCpu / s.value.count

            var varianceCpu: Long = 0
            var varianceGpu: Long = 0
            for (i in 0 until s.value.cpuTimes.size()) {
                val currentTimeCpu = s.value.cpuTimes.get(i)
                val currentTimeGpu = s.value.gpuTimes.get(i)
                varianceCpu += (currentTimeCpu - meanTimeCpu) * (currentTimeCpu - meanTimeCpu)
                varianceGpu += (currentTimeGpu - meanTimeGpu) * (currentTimeGpu - meanTimeGpu)
            }
            varianceCpu /= s.value.count.toLong()
            varianceGpu /= s.value.count.toLong()

            val deviationGpuInSeconds = Math.sqrt(varianceGpu.toDouble()).toFloat() / 1000f / 1000f
            val deviationCpuInSeconds = Math.sqrt(varianceCpu.toDouble()).toFloat() / 1000f / 1000f
            builder.append(String.format("%s\t| %.5f(%.5f)\t|%.5f(%.5f)\t|\t%s", clippedName, meanTimeGpu.toFloat() / 1000f / 1000f, deviationGpuInSeconds, meanTimeCpu.toFloat() / 1000f / 1000f, deviationCpuInSeconds, s.value.count))
            builder.append("\n")
        }

        return builder.toString()

    }

    fun calculateAverages(sampleCount: Int): Map<String, AverageHelper> {
        val averages = HashMap<String, AverageHelper>()

        for (i in collectedTimes.size downTo 1) {
            val record = collectedTimes[i - 1]
            var averageHelper: AverageHelper? = averages[record.name]
            if (averageHelper == null) {
                averageHelper = AverageHelper()
                averages[record.name] = averageHelper
            }
            if (averageHelper.count < sampleCount) {
                averageHelper.count++
                averageHelper.summedTime += record.timeGpu
                averageHelper.summedTimeCpu += record.timeCpu
                averageHelper.addGpuTime(record.timeGpu)
                averageHelper.addCpuTime(record.timeCpu)
            }
        }
        return averages
    }

    fun reset() {
        queryObjects = ArrayList()
        frameCounter = 0
        collectedTimes = ArrayList()
    }

    fun OpenGLProfilingTask.dump(builder: StringBuilder): StringBuilder = dump(0, builder)

    private fun OpenGLProfilingTask.dump(indentation: Int, builder: StringBuilder): StringBuilder {
        if (profiling) {

            var indentationString = ""
            for (i in (0 until indentation)) {
                indentationString += "  "
            }
            builder.append(indentationString)
            builder.append(String.format("%s : %.5fms (CPU: %.5fms)", name, timeTaken.toFloat() / 1000f / 1000f, timeTakenCpu.toFloat() / 1000f / 1000f))
            builder.append("\n")

            children.forEach {
                it.dump(indentation+1, builder)
            }
        }

        return builder
    }

    override fun dump(task: ProfilingTask?) = if(task != null) {
        currentTimings = task.dumpTimings()
        currentAverages = dumpAverages()
    } else {
        currentTimings = ""
        currentAverages = ""
    }

}