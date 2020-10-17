package de.hanno.hpengine.engine.lifecycle

import de.hanno.hpengine.engine.scene.Scene

interface Updatable {
    @JvmDefault
    suspend fun update(scene: Scene, deltaSeconds: Float) {}
}
