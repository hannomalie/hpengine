package de.hanno.hpengine.engine.extension

import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.Scene
import org.koin.core.definition.Definition
import org.koin.core.instance.InstanceFactory
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds


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
