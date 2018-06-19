package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity

interface ManagerRegistry {
    fun getManagers(): Collection<Manager>
    fun <T : Manager> get(managerClass: Class<T>): T
    fun update(deltaSeconds: Float) {
        getManagers().forEach{ it.update(deltaSeconds) }
    }
    fun <T : Manager> register(manager: T): T
    fun clearManager() {
        getManagers().forEach { it.clear() }
    }

    fun onEntityAdded(entities: List<Entity>) {
        getManagers().forEach {
            it.onEntityAdded(entities)
        }
    }

    fun afterUpdate() {
        getManagers().forEach {
            it.afterUpdate()
        }
    }
}

class SimpleManagerRegistry: ManagerRegistry {
    private val managers = mutableMapOf<Class<*>, Manager>()
    override fun getManagers(): Collection<Manager> = managers.values
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
    fun update(deltaSeconds: Float)
    fun <COMPONENT_TYPE, SYSTEM_TYPE : ComponentSystem<COMPONENT_TYPE>> register(system: SYSTEM_TYPE): SYSTEM_TYPE
    fun clearSystems() {
        getSystems().forEach { it.clear() }
    }

    fun onEntityAdded(entities: List<Entity>) {
        getSystems().forEach {
            it.onEntityAdded(entities)
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

    override fun update(deltaSeconds: Float) {
        systems.values.forEach { it.update(deltaSeconds) }
    }

}