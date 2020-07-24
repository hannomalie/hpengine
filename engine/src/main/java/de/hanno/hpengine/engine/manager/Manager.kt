package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface Manager: Updatable {
    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun onComponentAdded(component: Component) {}

    @JvmDefault
    fun extract(scene: Scene, renderState: RenderState) {}

    @JvmDefault
    fun beforeSetScene(nextScene: Scene) { }

    @JvmDefault
    fun afterSetScene() { }

}