package de.hanno.hpengine.stopwatch

import java.util.*
import java.util.logging.Logger

class StopWatch internal constructor() {
    companion object {
        val LOGGER = Logger.getLogger(StopWatch::class.java.name)
        var ACTIVE = false
        var PRINT = false
        var instance = StopWatch()

        var watches = LinkedList<Watch>()
        fun start(description: String) {
            if (!ACTIVE) {
                return
            }
            watches.addLast(Watch(System.nanoTime(), description))
        }

        fun stop(): Long {
            return if (!ACTIVE) {
                0
            } else System.currentTimeMillis() - watches.pollLast().start
        }

        fun stopAndGetStringMS(): String {
            if (!ACTIVE) {
                return ""
            }
            val watch = watches.pollLast()
            return String.format("%s took %.3f ms", watch.description, (System.nanoTime() - watch.start) / 1000000f)
        }

        fun stopAndPrintMS() {
            if (!ACTIVE) {
                return
            }
            if (!PRINT) {
                return
            }
            val out = stopAndGetStringMS()
            LOGGER.info(intendation + out)
        }

        private val intendation: String
            get() {
                var n = watches.size
                val result = StringBuilder()
                while (n > 0) {
                    result.append("..")
                    n--
                }
                return result.toString()
            }
    }
}