package de.hanno.hpengine.extension

import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.state.RenderSystem
import org.koin.core.definition.Definition
import org.koin.core.instance.InstanceFactory
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds


inline fun <reified T: DeferredRenderExtension> Module.renderExtension(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> = single(
    definition = definition
) binds arrayOf(T::class, DeferredRenderExtension::class)

inline fun <reified T: RenderSystem> Module.renderSystem(
    noinline definition: Definition<T>
): Pair<Module, InstanceFactory<*>> = single(
    definition = definition
) bind RenderSystem::class
