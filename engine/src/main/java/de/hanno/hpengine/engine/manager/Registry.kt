package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity

interface Registry {
    fun <T : Component> get(componentClass: Class<T>): ComponentSystem<T>
    fun getMangers(): List<ComponentSystem<*>>
    fun update(deltaSeconds: Float)
    fun <COMPONENT_TYPE, SYSTEM_TYPE : ComponentSystem<COMPONENT_TYPE>> register(system: SYSTEM_TYPE, clazz: Class<COMPONENT_TYPE>): SYSTEM_TYPE
}

interface ComponentSystem<T : Component> {
    fun update(deltaSeconds: Float)
    val components: List<T>
    fun create(entity: Entity): T
    fun addComponent(component: T)
}

class SimpleRegistry : Registry {

    override fun getMangers(): List<ComponentSystem<*>> = managers.values.toList()

    private val managers = mutableMapOf<Class<*>, ComponentSystem<*>>()

    override fun <COMPONENT_TYPE, T : ComponentSystem<COMPONENT_TYPE>> register(system: T, clazz: Class<COMPONENT_TYPE>): T {
        managers.put(clazz, system)
        return system
    }

    override fun <T : Component> get(componentClass: Class<T>): ComponentSystem<T> {
        if (!managers.contains(componentClass)) {
            throw IllegalStateException("Requested manager of componentClass $componentClass, but no manager registered.")
        }
        return (managers[componentClass] as ComponentSystem<T>?)!!
    }

    override fun update(deltaSeconds: Float) {
        managers.values.forEach { it.update(deltaSeconds) }
    }

}