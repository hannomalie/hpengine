package de.hanno.hpengine.stopwatch

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33
import java.util.logging.Logger

class OpenGLStopWatch {
    private var queryIdStart = 0
    private var queryIdEnd = 0
    private var description = ""
    fun start(description: String) {
        this.description = description
        queryIdStart = GL15.glGenQueries()
        queryIdEnd = GL15.glGenQueries()
        GL33.glQueryCounter(queryIdStart, GL33.GL_TIMESTAMP)
    }

    fun stop() {
        GL33.glQueryCounter(queryIdEnd, GL33.GL_TIMESTAMP)
    }

    val timeInMS: Long
        get() {
            var stopTimerAvailable: Long = 0
            while (stopTimerAvailable == 0L) {
                stopTimerAvailable = GL33.glGetQueryObjecti64(queryIdEnd, GL15.GL_QUERY_RESULT_AVAILABLE)
            }
            val start: Long = GL33.glGetQueryObjecti64(queryIdStart, GL15.GL_QUERY_RESULT)
            val end: Long = GL33.glGetQueryObjecti64(queryIdEnd, GL15.GL_QUERY_RESULT)
            return (end - start) / 1000000L
        }

    fun printTimeInMS() {
        LOGGER.info("$description took $timeInMS ms")
    }

    fun stopAndGetTimeInMS(): Long {
        stop()
        return timeInMS
    }

    fun stopAndPrintTimeInMS() {
        stop()
        printTimeInMS()
    }

    companion object {
        private val LOGGER = Logger.getLogger(OpenGLStopWatch::class.java.name)
    }
}