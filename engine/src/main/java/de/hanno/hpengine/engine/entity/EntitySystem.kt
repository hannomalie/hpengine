package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import kotlinx.coroutines.CoroutineScope

interface EntitySystem {
    @JvmDefault
    fun CoroutineScope.update(deltaSeconds: Float) {}
    fun gatherEntities()
    fun UpdateLock.onEntityAdded(entities: List<Entity>) {
        gatherEntities()
    }
    fun UpdateLock.onComponentAdded(component: Component) {
        gatherEntities()
    }

    fun clear()
    fun extract(renderState: RenderState) {}
}

interface EntitySystemRegistry {
    fun getSystems(): Collection<EntitySystem>
    fun CoroutineScope.update(deltaSeconds: Float) {
        for(system in this@EntitySystemRegistry.getSystems()){
            with(system) {
                update(deltaSeconds)
            }
        }
    }
    fun <T : EntitySystem> register(system: T): T
    fun gatherEntities() {
        for(system in getSystems()) { system.gatherEntities()}
    }

    fun <T: EntitySystem> get(clazz: Class<T>):T  {
        var firstOrNull: EntitySystem? = null
        for(it in getSystems()) {
            if(it::class.java == clazz) {
                firstOrNull = it
                break
            }
        }
        if(firstOrNull == null) {
            throw IllegalStateException("Requested entity system of class $clazz, but no system registered.")
        } else return clazz.cast(firstOrNull)
    }

    fun UpdateLock.onEntityAdded(entities: List<Entity>) {
        for(system in getSystems()) {
            with(system) { onEntityAdded(entities) }
        }
    }
    fun UpdateLock.onComponentAdded(component: Component) {
        for(system in getSystems()) {
            with(system) { onComponentAdded(component) }
        }
    }

    fun clearSystems() {
        for(system in getSystems()){ system.clear() }
    }
}

class SimpleEntitySystemRegistry: EntitySystemRegistry {
    val systems = mutableListOf<EntitySystem>()
    override fun getSystems(): Collection<EntitySystem> = systems

    override fun <T : EntitySystem> register(system: T): T {
        systems.add(system)
        return system
    }
}

abstract class SimpleEntitySystem(val scene: Scene, val componentClasses: List<Class<out Component>>) : EntitySystem {

    protected val entities = mutableListOf<Entity>()
    protected val components = mutableMapOf<Class<out Component>, List<Component>>().apply {
        componentClasses.forEach {
            this[it] = emptyList()
        }
    }

    override fun gatherEntities() {
        entities.clear()
        if(componentClasses.isEmpty()) {
            entities.addAll(scene.entityManager.getEntities())
        } else {
            entities.addAll(scene.entityManager.getEntities().filter { it.components.keys.containsAll(componentClasses) })
        }
        gatherComponents()
    }

    fun gatherComponents() {
        components.clear()
        if(componentClasses.isEmpty()) {
            for (entity in entities) {
                for (component in entity.components) {
                    val list: MutableList<Component> = mutableListOf(component.value)
                    if(components[component.key] != null) {
                        list.addAll(components[component.key]!!)
                    }
                    components[component.key] = list
                }
            }
        } else {
            for (clazz in componentClasses) {
                components[clazz] = entities.mapNotNull { entity ->  entity.getComponent(clazz) }
            }
        }
    }

    inline fun <reified T: Component> getComponents(type: Class<T>): List<T> {
        val list = components[type] ?: emptyList()
        return list as List<T>
    }

    override fun clear() {
        components.clear()
        entities.clear()
    }
}
