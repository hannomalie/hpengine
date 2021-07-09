package de.hanno.hpengine.engine.extension

import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.dsl.SceneDescription
import org.koin.core.definition.Definition
import org.koin.core.instance.InstanceFactory
import org.koin.core.module.Module
import org.koin.dsl.ScopeDSL
import org.koin.dsl.bind


interface Extension {
    fun extract(scene: Scene, renderState: RenderState) { }
    fun SceneDescription.decorate() { }
}

inline fun <reified T: ComponentSystem<*>> Module.componentSystem(
    noinline definition: Definition<T>
) {
    scope<Scene> { scoped(null, definition) bind ComponentSystem::class }
}
inline fun <reified T: EntitySystem> Module.entitySystem(
    noinline definition: Definition<T>
){
    scope<Scene> { scoped(null, definition) bind EntitySystem::class }
}
inline fun <reified T: Manager> Module.manager(
    noinline definition: Definition<T>
){
    scope<Scene> { scoped(null, definition) bind Manager::class }
}

inline fun <reified T: RenderExtension<*>> Module.renderExtension(
    createdAtStart: Boolean = false,
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> {
    return single(null, createdAtStart, definition) bind RenderExtension::class
}
inline fun <reified T: RenderSystem> Module.renderSystem(
    createdAtStart: Boolean = false,
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> {
    return single(null, createdAtStart, definition) bind RenderSystem::class
}
inline fun <reified T: Extension> Module.extension(
    createdAtStart: Boolean = true,
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> {
    return single(null, createdAtStart, definition) bind Extension::class
}
inline fun <reified T: RenderSystem> ScopeDSL.renderSystem(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> {
    return scoped(null, definition) bind RenderSystem::class
}
