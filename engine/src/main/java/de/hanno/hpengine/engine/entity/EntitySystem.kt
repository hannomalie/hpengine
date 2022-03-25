package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene

interface EntitySystem {
    suspend fun update(scene: Scene, deltaSeconds: Float) {}
    fun gatherEntities(scene: Scene)
    fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        gatherEntities(scene)
    }
    fun onComponentAdded(scene: Scene, component: Component) {
        gatherEntities(scene)
    }

    fun clear()
    fun extract(renderState: RenderState) {}
}
