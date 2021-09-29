package de.hanno.hpengine.engine.lifecycle

import de.hanno.hpengine.engine.scene.Scene

interface Updatable {
    suspend fun update(scene: Scene, deltaSeconds: Float) {}
}
