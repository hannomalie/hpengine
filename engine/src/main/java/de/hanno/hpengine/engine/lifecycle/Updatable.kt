package de.hanno.hpengine.engine.lifecycle

import kotlinx.coroutines.CoroutineScope


interface Updatable {
    @JvmDefault
    fun CoroutineScope.update(deltaSeconds: Float) {}
}
