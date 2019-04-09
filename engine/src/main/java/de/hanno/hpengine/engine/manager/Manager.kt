package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState

interface Manager {
    fun update(deltaSeconds: Float) {}

    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun afterUpdate(deltaSeconds: Float) {}

    @JvmDefault
    fun extract(renderState: RenderState) {}
}