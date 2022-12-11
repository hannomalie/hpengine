package de.hanno.hpengine.stopwatch

import kotlin.math.max

class Record private constructor(val name: String, val timeGpu: Long, val timeCpu: Long) {
    companion object {
        operator fun invoke(name: String, timeGpu: Long, timeCpu: Long): Record {
            return Record(name, max(timeGpu, 0), max(timeCpu, 0))
        }
    }
}