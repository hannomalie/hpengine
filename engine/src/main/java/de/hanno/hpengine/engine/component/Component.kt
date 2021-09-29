package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import java.io.Serializable

interface Component : Updatable, Serializable {

    val entity: Entity

    override suspend fun update(scene: Scene, deltaSeconds: Float) {}

    fun destroy() {}
}
