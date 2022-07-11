package de.hanno.hpengine.engine.graphics

interface GpuCommandSync {
    val isSignaled: Boolean
        get() = true

    fun delete() {}
}