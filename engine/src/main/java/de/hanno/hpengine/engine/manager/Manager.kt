package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SingleThreadContext
import kotlinx.coroutines.CoroutineScope

interface Manager {
    @JvmDefault
    fun CoroutineScope.update(deltaSeconds: Float) {}

    @JvmDefault
    fun CoroutineScope.afterUpdate(deltaSeconds: Float) {}

    @JvmDefault
    fun clear() {}

    @JvmDefault
    fun SingleThreadContext.onEntityAdded(entities: List<Entity>) {}

    @JvmDefault
    fun SingleThreadContext.onComponentAdded(component: Component) {}

    @JvmDefault
    fun extract(renderState: RenderState) {}

    @JvmDefault
    fun onSetScene(nextScene: Scene) { }

}