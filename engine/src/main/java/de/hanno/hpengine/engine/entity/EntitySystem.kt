package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface EntitySystem {
    @JvmDefault
    suspend fun update(scene: Scene, deltaSeconds: Float) {}
    fun gatherEntities(scene: Scene)
    fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        gatherEntities(scene)
    }
    fun onComponentAdded(scene: Scene, component: Component) {
        gatherEntities(scene)
    }

    fun clear()
    fun extract(renderState: RenderState) {}
}

interface EntitySystemRegistry {
    fun getSystems(): Collection<EntitySystem>
    suspend fun update(scene: Scene, deltaSeconds: Float) {
        for(system in this@EntitySystemRegistry.getSystems()) {
            system.update(scene, deltaSeconds)
        }
    }
    fun <T : EntitySystem> register(system: T): T
    fun gatherEntities(scene: Scene) {
        for(system in getSystems()) { system.gatherEntities(scene)
        }
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

    fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        for(system in getSystems()) {
            with(system) { onEntityAdded(scene, entities) }
        }
    }
    fun onComponentAdded(scene: Scene, component: Component) {
        for(system in getSystems()) {
            with(system) { onComponentAdded(scene, component) }
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

abstract class SimpleEntitySystem(val componentClasses: List<Class<out Component>>) : EntitySystem {

    protected val entities = mutableListOf<Entity>()
    protected val components = mutableListOf<Component>()

    override fun gatherEntities(scene: Scene) {
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

    fun <T: Component> getComponents(type: Class<T>): List<T> = components.filterIsInstance(type)

    override fun clear() {
        components.clear()
        entities.clear()
    }
}
