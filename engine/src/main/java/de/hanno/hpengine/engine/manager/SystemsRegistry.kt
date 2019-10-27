package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import kotlinx.coroutines.CoroutineScope

interface ManagerRegistry {
    val managers: Map<Class<*>, Manager>
    fun <T : Manager> get(managerClass: Class<T>): T
    fun CoroutineScope.update(deltaSeconds: Float) {
        this@ManagerRegistry.managers.forEach{
            with(it.value) { update(deltaSeconds) }
        }
    }
    fun <T : Manager> register(manager: T): T
    fun clearManagers() {
        managers.forEach { it.value.clear() }
    }

    fun onEntityAdded(entities: List<Entity>) {
        managers.forEach {
            it.value.onEntityAdded(entities)
        }
    }

    fun CoroutineScope.afterUpdate(deltaSeconds: Float) {
        managers.forEach { manager ->
            with(manager.value) { afterUpdate(deltaSeconds) }
        }
    }
}

class SimpleManagerRegistry: ManagerRegistry {
    override val managers = mutableMapOf<Class<*>, Manager>()
    override fun <T : Manager> register(manager: T): T {
        managers[manager::class.java] = manager
        return manager
    }
    override fun <T : Manager> get(managerClass: Class<T>) = managerClass.cast(managers.get(managerClass))

}

interface SystemsRegistry {
    fun <T : ComponentSystem<*>> get(systemClass: Class<T>): T
    fun <T : Component> getForComponent(componentClass: Class<T>): ComponentSystem<T>

    fun getSystems(): List<ComponentSystem<*>>
    fun CoroutineScope.update(deltaSeconds: Float)
    fun <COMPONENT_TYPE, SYSTEM_TYPE : ComponentSystem<COMPONENT_TYPE>> register(system: SYSTEM_TYPE): SYSTEM_TYPE
    fun clearSystems() {
        getSystems().forEach { it.clear() }
    }

    fun onEntityAdded(entities: List<Entity>) {
        val matchedComponents = mutableMapOf<Class<out Component>, Component>()
        getSystems().forEach {
            matchedComponents += it.onEntityAdded(entities)
        }
        val leftOvers = matchedComponents.keys.filter { !matchedComponents.keys.contains(it) }
        if(leftOvers.isNotEmpty()) {
            throw IllegalStateException("Cannot find system for components: ${leftOvers.map { it::class.java }}")
        }
    }
}

class ComponentSystemRegistry : SystemsRegistry {
    override fun getSystems(): List<ComponentSystem<*>> = systems.values.toList()

    private val systems = mutableMapOf<Class<*>, ComponentSystem<*>>()

    override fun <COMPONENT_TYPE, T : ComponentSystem<COMPONENT_TYPE>> register(system: T): T {
        systems[system.componentClass] = system
        return system
    }

    override fun <T : ComponentSystem<*>> get(systemClass: Class<T>): T {
        val systemOrNull = systems.map { it.value }.firstOrNull { it::class.java == systemClass }
        if (systemOrNull == null) {
            throw IllegalStateException("Requested system of class $systemClass, but no system registered.")
        } else  return systemClass.cast(systemOrNull)
    }

    override fun <T : Component> getForComponent(componentClass: Class<T>): ComponentSystem<T> {
        return systems[componentClass] as ComponentSystem<T>
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        for (system in systems.values) {
            with(system) {
                update(deltaSeconds)
            }
        }
    }

}