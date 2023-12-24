package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.graphics.sync.GpuCommandSync
import de.hanno.hpengine.graphics.GraphicsApi

interface IRenderState {
    var gpuCommandSync: GpuCommandSync
}

class RenderState(
    private val graphicsApi: GraphicsApi,
) : IRenderState {

    val customState = CustomStates()

    var cycle: Long = 0
    var time = System.currentTimeMillis()
    var deltaSeconds: Float = 0.1f
    override var gpuCommandSync: GpuCommandSync = graphicsApi.createCommandSync()

    val gpuHasFinishedUsingIt get() = gpuCommandSync.isSignaled

    fun add(index: Int, state: Any) = customState.add(index, state)

    operator fun <T: Any> get(stateRef: StateRef<T>) = customState[stateRef]
    fun <T: Any> set(ref: StateRef<T>, value: T) { customState.set(ref, value) }
}

class CustomStates {
    private val states = mutableListOf<Any>()

    fun add(index: Int, state: Any) {
        states.add(index, state)
    }

    operator fun <T: Any> get(ref: StateRef<T>) = states[ref.index] as T
    fun <T: Any> set(ref: StateRef<T>, value: T) { states[ref.index] = value }

    fun clear() = states.clear()
}

class StateRef<out T>(val index: Int)
