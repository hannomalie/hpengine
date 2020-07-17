package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface EntitySystem {
    @JvmDefault
    fun CoroutineScope.update(deltaSeconds: Float) {}
    fun gatherEntities()
    fun onEntityAdded(entities: List<Entity>) {
        gatherEntities()
    }
    fun onComponentAdded(component: Component) {
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

    fun onEntityAdded(entities: List<Entity>) {
        for(system in getSystems()) {
            with(system) { onEntityAdded(entities) }
        }
    }
    fun onComponentAdded(component: Component) {
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
    protected val components = mutableListOf<Component>()

    override fun gatherEntities() {
        entities.clear()
        if(componentClasses.isEmpty()) {
            entities.addAll(scene.entityManager.getEntities())
        } else {
            entities.addAll(scene.entityManager.getEntities().filter { it.hasComponents(componentClasses) })
        }
        gatherComponents()
    }

    fun gatherComponents() {
        components.clear()
        if(componentClasses.isEmpty()) {
            for (entity in entities) {
                components.addAll(entity.getComponents(componentClasses))
            }
        } else {
            for (clazz in componentClasses) {
                components.addAll(entities.flatMap { entity ->  entity.getComponents(clazz) })
            }
        }
    }

    inline fun <reified T: Component> getComponents(type: Class<T>): List<T> = components.filterIsInstance<T>()

    override fun clear() {
        components.clear()
        entities.clear()
    }
}
