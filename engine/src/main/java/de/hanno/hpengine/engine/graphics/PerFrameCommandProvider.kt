package de.hanno.hpengine.engine.graphics

import java.util.concurrent.atomic.AtomicInteger

interface PerFrameCommandProvider {
    val drawCommand: Runnable

    fun isReadyForExecution(): Boolean

    fun setReadyForExecution()

    fun postRun()

    fun execute() {
        drawCommand.run()
        postRun()
    }

    fun executeAfterFrame() {}
}

open class SimpleProvider(command: Runnable): PerFrameCommandProvider {
    override val drawCommand = command

    private val drawCounter = AtomicInteger(-1)

    override fun setReadyForExecution() {
        drawCounter.getAndSet(0)
    }

    override fun postRun() {
        drawCounter.getAndIncrement()
    }

    override fun isReadyForExecution() = drawCounter.get() == 0
}