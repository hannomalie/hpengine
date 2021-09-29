package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene

interface EntitySystem {
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

abstract class SimpleEntitySystem(val componentClasses: List<Class<out Component>>) : EntitySystem {

    protected val entities = mutableListOf<Entity>()
    protected val components = mutableListOf<Component>()

    override fun gatherEntities(scene: Scene) {
        entities.clear()
        if(componentClasses.isEmpty()) {
            entities.addAll(scene.getEntities())
        } else {
            entities.addAll(scene.getEntities().filter { it.hasComponents(componentClasses) })
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
