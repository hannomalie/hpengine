package de.hanno.hpengine.engine.graphics.state.multithreading

import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateRef
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

class TripleBuffer<T : RenderState> constructor(private val instanceA: T,
                                                private val instanceB: T,
                                                private val instanceC: T,
                                                private val preventSwap: (T, T) -> Boolean = { _,_ -> false }) {

    constructor(factory: () -> T, preventSwap: (T, T) -> Boolean = { _,_ -> false }): this(factory(), factory(), factory(), preventSwap)

    private val swapLock = ReentrantLock()
    private val stagingLock = ReentrantLock()

    var currentReadState: T = instanceA
        private set
    var currentWriteState: T = instanceB
        private set
    private var currentStagingState: T = instanceC

    private var tempA: T? = null
    private var tempB: T? = null

    protected fun swap(): Boolean = swapLock.withLock {
        stagingLock.withLock {
            if (!preventSwap(currentStagingState, currentReadState)) {
                tempA = currentReadState
                currentReadState = currentStagingState
                currentStagingState = tempA!!
                true
            } else false
        }
    }

    fun swapStaging() {
        stagingLock.lock()
        tempB = currentStagingState
        currentStagingState = currentWriteState
        currentWriteState = tempB!!
        stagingLock.unlock()
    }

    private var customStateCounter = 0
    fun <TYPE : Any> registerState(factory: () -> TYPE): StateRef<TYPE> {
        val newIndex = customStateCounter++
        instanceA.add(factory())
        instanceB.add(factory())
        instanceC.add(factory())
        return StateRef(newIndex)
    }

    fun startRead() = swapLock.lock()

    fun stopRead(): Boolean = swap()

    fun logState() {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.fine("Read  " + if (currentReadState === instanceA) 0 else if (currentReadState === instanceB) 1 else 2)
            LOGGER.fine("Stage " + if (currentStagingState === instanceA) 0 else if (currentStagingState === instanceB) 1 else 2)
            LOGGER.fine("Write " + if (currentWriteState === instanceA) 0 else if (currentWriteState === instanceB) 1 else 2)
        }
    }

    fun printState() {
        println("Read  " + (if (currentReadState === instanceA) 0 else if (currentReadState === instanceB) 1 else 2) + " with cycle " + currentReadState.cycle)
        println("Stage " + (if (currentStagingState === instanceA) 0 else if (currentStagingState === instanceB) 1 else 2) + " with cycle " + currentStagingState.cycle)
        println("Write " + (if (currentWriteState === instanceA) 0 else if (currentWriteState === instanceB) 1 else 2) + " with cycle " + currentWriteState.cycle)
    }

    companion object {
        private val LOGGER = Logger.getLogger(TripleBuffer::class.java.name)
    }

}