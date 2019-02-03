package de.hanno.hpengine.engine.entity

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import net.engio.mbassy.listener.Handler

interface EntitySystem {
    fun update(deltaSeconds: Float)
    fun gatherEntities()
    fun onEntityAdded(entities: List<Entity>) {
        gatherEntities()
    }

    fun clear()
    fun extract(renderState: RenderState) {}
}

interface EntitySystemRegistry {
    fun getSystems(): Collection<EntitySystem>
    fun update(deltaSeconds: Float) {
        for(system in getSystems()){ system.update(deltaSeconds) }
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
            system.onEntityAdded(entities)
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

abstract class SimpleEntitySystem(val engine: Engine, val scene: Scene, val componentClasses: List<Class<out Component>>) : EntitySystem {

    protected val entities = mutableListOf<Entity>()
    protected val components = mutableMapOf<Class<out Component>, List<Component>>().apply {
        componentClasses.forEach {
            this[it] = emptyList()
        }
    }

    init {
        engine.eventBus.register(this)
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
                components[clazz] = entities.map { entity ->  entity.getComponent(clazz) }
            }
        }
    }

    inline fun <reified T: Component> getComponents(type: Class<T>): List<T> {
        val list = components[type] ?: emptyList()
        return list as List<T>
    }

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        engine.commandQueue.execute( Runnable {
            gatherEntities()
            gatherComponents()
        }, false)
    }

    override fun clear() {
        components.clear()
        entities.clear()
    }
}
