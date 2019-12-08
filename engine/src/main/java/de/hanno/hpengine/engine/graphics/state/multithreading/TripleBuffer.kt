package de.hanno.hpengine.engine.graphics.state.multithreading

import de.hanno.hpengine.engine.graphics.state.CustomState
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateRef
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import java.util.logging.Level
import java.util.logging.Logger

class TripleBuffer<T : RenderState>(private val instanceA: T,
                                     private val instanceB: T,
                                     private val instanceC: T) {

    private val swapLock = ReentrantLock()
    private val stagingLock = ReentrantLock()

    var currentReadState: T = instanceA
        private set
    var currentWriteState: T = instanceB
        private set
    private var currentStagingState: T = instanceC

    private var tempA: T? = null
    private var tempB: T? = null

    protected fun swap(): Boolean {
        var swapped = false
        swapLock.lock()
        stagingLock.lock()
        if (!currentReadState.preventSwap(currentStagingState, currentReadState)) {
            tempA = currentReadState
            currentReadState = currentStagingState
            currentStagingState = tempA!!
            swapped = true
        }
        stagingLock.unlock()
        swapLock.unlock()
        return swapped
    }

    protected fun swapStaging() {
        stagingLock.lock()
        tempB = currentStagingState
        currentStagingState = currentWriteState
        currentWriteState = tempB!!
        stagingLock.unlock()
    }

    fun update(): Boolean {
        currentWriteState.customState.update(currentWriteState)
        swapStaging()
        return true
    }

    private var customStateCounter = 0
    fun <TYPE : CustomState> registerState(factory: () -> TYPE): StateRef<TYPE> {
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

    private class State<T : RenderState>(private val state: T)

    companion object {
        private val LOGGER = Logger.getLogger(TripleBuffer::class.java.name)
    }

}