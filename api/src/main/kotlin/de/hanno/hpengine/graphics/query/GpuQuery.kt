package de.hanno.hpengine.graphics.query

interface GpuQuery<RESULT> {
    fun begin()
    fun end()
    fun resultsAvailable(): Boolean

    val queryToWaitFor: Int
    val result: RESULT
}