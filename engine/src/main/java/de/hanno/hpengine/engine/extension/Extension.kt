package de.hanno.hpengine.engine.extension

import com.artemis.BaseSystem
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.dsl.SceneDescription
import org.koin.core.definition.Definition
import org.koin.core.instance.InstanceFactory
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds


interface Extension {
    fun extract(scene: Scene, renderState: RenderState) { }
    fun SceneDescription.decorate() { }
    suspend fun update(scene: Scene, deltaSeconds: Float) { }
}

inline fun <reified T: ComponentSystem<*>> Module.componentSystem(
    noinline definition: Definition<T>
) {
    scope<Scene> { scoped(definition = definition) bind ComponentSystem::class }
}
inline fun <reified T> Module.baseComponentSystem(
    noinline definition: Definition<T>
) where T: ComponentSystem<*>, T: BaseSystem {
    scope<Scene> { scoped(definition = definition) bind ComponentSystem::class }
}
inline fun <reified T: EntitySystem> Module.entitySystem(
    noinline definition: Definition<T>
){
    scope<Scene> { scoped(definition = definition) bind EntitySystem::class }
}
inline fun <reified T: Manager> Module.manager(
    noinline definition: Definition<T>
){
    scope<Scene> { scoped(definition = definition) binds arrayOf(T::class, Manager::class) }
}

inline fun <reified T: DeferredRenderExtension<*>> Module.renderExtension(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> = single(
    definition = definition
) binds arrayOf(T::class, DeferredRenderExtension::class)

inline fun <reified T: RenderSystem> Module.renderSystem(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> = single(
    definition = definition
) bind RenderSystem::class

inline fun <reified T: Extension> Module.extension(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> = single(
    definition = definition
) binds arrayOf(T::class, Extension::class)
