package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

import java.io.Serializable

interface Component : Updatable, Serializable {

    val entity: Entity

    @JvmDefault
    override suspend fun update(scene: Scene, deltaSeconds: Float) {}

    @JvmDefault
    fun destroy() {}
}
