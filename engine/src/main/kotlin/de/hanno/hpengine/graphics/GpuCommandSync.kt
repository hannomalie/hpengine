package de.hanno.hpengine.graphics

interface GpuCommandSync {
    val isSignaled: Boolean
        get() = true

    fun delete() {}
}