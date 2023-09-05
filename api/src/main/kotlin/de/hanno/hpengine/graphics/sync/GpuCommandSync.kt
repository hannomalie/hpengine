package de.hanno.hpengine.graphics.sync

interface GpuCommandSync {
    val isSignaled: Boolean
    val onSignaled: (() -> Unit)?

    fun delete() {}
    fun await()
    fun update()
}