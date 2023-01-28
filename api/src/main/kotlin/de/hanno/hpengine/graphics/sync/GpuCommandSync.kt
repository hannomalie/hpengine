package de.hanno.hpengine.graphics.sync

interface GpuCommandSync {
    val isSignaled: Boolean

    fun delete() {}
    fun await()
    fun update()
}