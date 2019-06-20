package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import kotlinx.coroutines.CoroutineScope

interface Manager {
    fun CoroutineScope.update(deltaSeconds: Float) {}

    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun CoroutineScope.afterUpdate(deltaSeconds: Float) {}

    @JvmDefault
    fun extract(renderState: RenderState) {}
}