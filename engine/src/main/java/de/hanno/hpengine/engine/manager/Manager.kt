package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface Manager {
    fun CoroutineScope.update(deltaSeconds: Float) {}

    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun CoroutineScope.onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun CoroutineScope.onComponentAdded(component: Component) {}

    @JvmDefault
    fun CoroutineScope.afterUpdate(deltaSeconds: Float) {}

    @JvmDefault
    fun extract(renderState: RenderState) {}

    @JvmDefault
    fun onSetScene(nextScene: Scene) { }

}