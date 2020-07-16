package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import kotlinx.coroutines.CoroutineScope

interface Manager {
    @JvmDefault
    fun CoroutineScope.update(deltaSeconds: Float) {}

    @JvmDefault
    fun CoroutineScope.afterUpdate(deltaSeconds: Float) {}

    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun onComponentAdded(component: Component) {}

    @JvmDefault
    fun extract(renderState: RenderState) {}

    @JvmDefault
    fun beforeSetScene(nextScene: Scene) { }

    @JvmDefault
    fun afterSetScene() { }

}