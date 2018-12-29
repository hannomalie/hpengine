package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState

interface ComponentSystem<T : Component> {
    fun update(deltaSeconds: Float) {
        for(component in getComponents()) {
            component.update(deltaSeconds)
        }
    }
    fun getComponents(): List<T>
    fun create(entity: Entity): T
    fun addComponent(component: T)
    fun clear()
    fun extract(renderState: RenderState) {}
    fun onSceneSet() {
        clear()
    }
    fun onEntityAdded(entities: List<Entity>) {
        for(entity in entities) {
            addCorrespondingComponents(entity.components)
        }
    }

    fun addCorrespondingComponents(components: Map<Class<Component>, Component>) {
        components.filter { it.key == componentClass }.forEach { addComponent(it.value as T) }
    }

    val componentClass: Class<T>
}

open class SimpleComponentSystem<T: Component>(componentClass: Class<T>, theComponents: List<T> = emptyList(), val factory: (Entity) -> T) : ComponentSystem<T> {
    private val components = mutableListOf<T>().apply { addAll(theComponents) }

    override fun getComponents(): List<T> = components

    override fun create(entity: Entity) = factory(entity)

    override fun addComponent(component: T) {
        components.add(component)
    }

    override fun clear() {
        components.clear()
    }

    override val componentClass: Class<T> = componentClass

}