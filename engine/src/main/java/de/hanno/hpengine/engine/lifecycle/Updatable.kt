package de.hanno.hpengine.engine.lifecycle

import kotlinx.coroutines.CoroutineScope


interface Updatable {
    @JvmDefault
    fun CoroutineScope.update(scene: de.hanno.hpengine.engine.scene.Scene, deltaSeconds: kotlin.Float) {}
}
