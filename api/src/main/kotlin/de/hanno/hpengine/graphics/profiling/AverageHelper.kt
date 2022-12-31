package de.hanno.hpengine.graphics.profiling

import com.carrotsearch.hppc.LongArrayList

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