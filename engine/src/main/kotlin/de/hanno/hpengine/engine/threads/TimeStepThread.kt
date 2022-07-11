package de.hanno.hpengine.engine.threads

import java.lang.InterruptedException
import de.hanno.hpengine.engine.threads.TimeStepThread
import java.util.concurrent.TimeUnit
import java.util.logging.LogManager
import java.util.logging.Logger

abstract class TimeStepThread(name: String, val minimumCycleTimeInSeconds: Float) : Thread() {
    private var start = 0L
    protected var lastFrame = 0L
    var stopRequested = false

    init {
        this.name = name
        uncaughtExceptionHandler = UncaughtExceptionHandler { _: Thread?, e: Throwable ->
            e.printStackTrace()
        }
    }

    //TODO: Consider this public
    protected constructor(name: String) : this(name, 0.0f) {}

    override fun start() {
        isDaemon = true
        super.start()
        start = System.nanoTime()
        lastFrame = System.nanoTime()
    }

    override fun run() {
        setName(name)
        while (!stopRequested) {
            val ns = System.nanoTime() - lastFrame
            val seconds = TimeUnit.NANOSECONDS.toSeconds(ns).toFloat()
            update(seconds)
            waitIfNecessary(seconds)
            lastFrame = System.nanoTime()
        }
        cleanup()
    }

    protected fun waitIfNecessary(actualS: Float) {
        var secondsLeft = minimumCycleTimeInSeconds - actualS
        if (secondsLeft <= 0) {
            return
        }
        while (secondsLeft >= 0) {
            try {
                val timeBeforeSleep = System.nanoTime()
                sleep(0, 100000)
                val sleptNanoSeconds = System.nanoTime() - timeBeforeSleep
                val sleptSeconds = sleptNanoSeconds / 1000000000.0
                secondsLeft -= sleptSeconds.toFloat()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun cleanup() {}
    abstract fun update(seconds: Float)
}