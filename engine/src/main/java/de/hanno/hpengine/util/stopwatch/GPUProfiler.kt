package de.hanno.hpengine.util.stopwatch

import com.carrotsearch.hppc.LongArrayList

import java.util.ArrayList
import java.util.HashMap

import org.lwjgl.opengl.GL15.glGenQueries
import org.lwjgl.opengl.GL33.GL_TIMESTAMP
import org.lwjgl.opengl.GL33.glQueryCounter
import kotlin.math.max

object GPUProfiler {
    var dumpAveragesRequested = false
    var porfiling = false
    var printing = false

    private var queryObjects: ArrayList<Int> = ArrayList()

    private var frameCounter: Int = 0

    internal var currentTask: ProfilingTask? = null

    var collectedTimes: ArrayList<Record> = ArrayList()

    val query: Int
        get() {
            val query: Int = if (queryObjects.isNotEmpty()) {
                queryObjects.removeAt(queryObjects.size - 1)
            } else {
                glGenQueries()
            }

            glQueryCounter(query, GL_TIMESTAMP)

            return query
        }
    var currentTimings = ""
    var currentAverages = ""

    fun start(name: String): ProfilingTask? = if (porfiling) {
        val newTask = ProfilingTask(name, currentTask)
        currentTask = newTask
        newTask
    } else null

    @JvmOverloads
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

    class Record private constructor(val name: String, val timeGpu: Long, val timeCpu: Long) {
        companion object {
            operator fun invoke(name: String, timeGpu: Long, timeCpu: Long): Record {
                return Record(name, max(timeGpu, 0), max(timeCpu, 0))
            }
        }
    }

    class AverageHelper {
        var count = 0
        var summedTime: Long = 0
        var summedTimeCpu: Long = 0
        val averageInMS: Long
            get() = summedTime / count / 1000 / 1000
        val averageCpuInMS: Long
            get() = summedTimeCpu / count / 1000 / 1000
        var cpuTimes = LongArrayList()
        var gpuTimes = LongArrayList()

        fun addCpuTime(time: Long) {
            cpuTimes.add(time)
        }

        fun addGpuTime(time: Long) {
            gpuTimes.add(time)
        }
    }


    fun ProfilingTask.dump(builder: StringBuilder): StringBuilder {
        return dump(0, builder)
    }

    private fun ProfilingTask.dump(indentation: Int, builder: StringBuilder): StringBuilder {
        if (printing) {

            var indentationString = ""
            for (i in (0 until indentation)) {
                indentationString += "    "
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

    fun dump() {
        currentTimings = currentTask?.dumpTimings() ?: ""
        currentAverages = dumpAverages()
        currentTask = null
    }

}

