package de.hanno.hpengine.engine.entity

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.scene.Scene
import net.engio.mbassy.listener.Handler

interface EntitySystem {
    fun update(deltaSeconds: Float)
    fun gatherEntities()
    fun onEntityAdded() {
        gatherEntities()
    }
}

interface EntitySystemRegistry {
    fun getSystems(): Collection<EntitySystem>
    fun update(deltaSeconds: Float) {
        getSystems().forEach{ it.update(deltaSeconds) }
    }
    fun <T : EntitySystem> register(manager: T): T
    fun gatherEntities() {
        getSystems().forEach { it.gatherEntities()}
    }
}

class SimpleEntitySystemRegistry: EntitySystemRegistry {
    val systems = mutableListOf<EntitySystem>()
    override fun getSystems(): Collection<EntitySystem> = systems

    override fun <T : EntitySystem> register(system: T): T {
        systems.add(system)
        return system
    }

    fun onEntityAdded() {
        systems.forEach {
            it.onEntityAdded()
        }
    }
}

abstract class SimpleEntitySystem(val engine: Engine, val scene: Scene, val componentClasses: List<Class<out Component>>,
                                  private val componentKeys: List<String> = componentClasses.map { it.simpleName }) : EntitySystem {

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
        if(componentKeys.isEmpty()) {
            entities.addAll(scene.getEntities())
        } else {
            entities.addAll(scene.getEntities().filter { it.components.keys.containsAll(componentKeys) })
        }
    }

    fun gatherComponents() {
        components.clear()
        componentClasses.forEach {
            components[it] = entities.map { entity ->  entity.getComponent(it) }
        }
    }

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        engine.commandQueue.execute({
            gatherEntities()
            gatherComponents()
        }, false)
    }
}