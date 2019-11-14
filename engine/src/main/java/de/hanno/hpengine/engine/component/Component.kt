package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import kotlinx.coroutines.CoroutineScope

import java.io.Serializable

interface Component : Updatable, Serializable {

    val entity: Entity

    @JvmDefault
    override fun CoroutineScope.update(deltaSeconds: Float) {}

    @JvmDefault
    fun destroy() {}
}
