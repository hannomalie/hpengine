package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface ComponentSystem<T : Component> {
    fun CoroutineScope.update(deltaSeconds: Float) {
        for(component in getComponents()) {
            launch {
                with(component) {
                    update(deltaSeconds)
                }
            }
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
    fun onEntityAdded(entities: List<Entity>): MutableMap<Class<out Component>, Component> {
        val matchedComponents = mutableMapOf<Class<out Component>, Component>()
        for(entity in entities) {
            matchedComponents += addCorrespondingComponents(entity.components)
        }
        return matchedComponents
    }


    fun onComponentAdded(component: Component) {
        addCorrespondingComponents(mapOf(component::class.java to component))
    }

    fun addCorrespondingComponents(components: Map<Class<out Component>, Component>): Map<Class<out Component>, Component> {
        val correspondingComponents = components.filter { it.key == componentClass }
        correspondingComponents.forEach { addComponent(it.value as T) }
        return correspondingComponents
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