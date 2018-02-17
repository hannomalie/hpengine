package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity

interface Registry {
    fun <T : ComponentSystem<*>> get(systemClass: Class<T>): T
    fun getSystems(): List<ComponentSystem<*>>
    fun update(deltaSeconds: Float)
    fun <COMPONENT_TYPE, SYSTEM_TYPE : ComponentSystem<COMPONENT_TYPE>> register(system: SYSTEM_TYPE): SYSTEM_TYPE
}

interface ComponentSystem<T : Component> {
    fun update(deltaSeconds: Float)
    val components: List<T>
    fun create(entity: Entity): T
    fun addComponent(component: T)
}

class SimpleRegistry : Registry {

    override fun getSystems(): List<ComponentSystem<*>> = systems.values.toList()

    private val systems = mutableMapOf<Class<*>, ComponentSystem<*>>()

    override fun <COMPONENT_TYPE, T : ComponentSystem<COMPONENT_TYPE>> register(system: T): T {
        systems.put(system::class.java, system)
        return system
    }

    override fun <T : ComponentSystem<*>> get(systemClass: Class<T>): T {
        if (!systems.contains(systemClass)) {
            throw IllegalStateException("Requested system of class $systemClass, but no system registered.")
        }
        return (systems[systemClass] as T)
    }

    override fun update(deltaSeconds: Float) {
        systems.values.forEach { it.update(deltaSeconds) }
    }

}