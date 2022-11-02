package de.hanno.hpengine.graphics

interface GpuCommandSync {
    val isSignaled: Boolean

    fun delete() {}
}