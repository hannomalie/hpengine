package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene

interface Manager: Updatable {
    fun clear() {}
    fun onEntityAdded(entities: List<Entity>) {}
    fun onComponentAdded(component: Component) {}
    fun extract(scene: Scene, renderState: RenderState) {}
    fun beforeSetScene(nextScene: Scene) { }
    fun afterSetScene(lastScene: Scene?, currentScene: Scene) { }
}