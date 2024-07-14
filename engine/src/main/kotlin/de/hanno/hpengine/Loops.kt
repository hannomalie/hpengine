package de.hanno.hpengine

import kotlinx.coroutines.*
import kotlin.math.min

fun launchEndlessLoop(loopCondition: () -> Boolean, actualUpdateStep: suspend (Float) -> Unit) {
        runBlocking {
            var currentTimeNs = System.nanoTime()
            val dtS = 0.001

            while (loopCondition()) {
                val newTimeNs = System.nanoTime()
                val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
                var frameTimeS = frameTimeNs / 1000000000.0
                currentTimeNs = newTimeNs
                while (frameTimeS > 0.0) {
                    val deltaTime = min(frameTimeS, dtS)
                    val deltaSeconds = deltaTime.toFloat()

                    try {
                        actualUpdateStep(deltaSeconds)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    frameTimeS -= deltaTime
                }
            }
        }
}
