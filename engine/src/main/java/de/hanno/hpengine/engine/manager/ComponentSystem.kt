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
    fun addComponent(component: T)
    fun clear()
    fun extract(renderState: RenderState) {}
    fun onSceneSet() {
        clear()
    }
    fun CoroutineScope.onEntityAdded(entities: List<Entity>): MutableMap<Class<out Component>, Component> {
        return onEntityAddedImpl(entities)
    }

//     Workaround for https://youtrack.jetbrains.com/issue/KT-11488?_ga=2.92346137.567661805.1573652933-1826229974.1518078622
    fun onEntityAddedImpl(entities: List<Entity>): MutableMap<Class<out Component>, Component> {
        val matchedComponents = mutableMapOf<Class<out Component>, Component>()
        for (entity in entities) {
            matchedComponents += addCorrespondingComponents(entity.components)
        }
        return matchedComponents
    }


    fun onComponentAdded(component: Component) {
        addCorrespondingComponents(mapOf(component::class.java to component))
    }

    fun addCorrespondingComponents(components: Map<Class<out Component>, Component>): Map<Class<out Component>, Component> {
        val correspondingComponents = components.filter { it.key == componentClass || componentClass.isAssignableFrom(it.key)}
        correspondingComponents.forEach { addComponent(it.value as T) }
        return correspondingComponents
    }

    val componentClass: Class<T>
}

open class SimpleComponentSystem<T: Component>(override val componentClass: Class<T>, theComponents: List<T> = emptyList()) : ComponentSystem<T> {
    private val components = mutableListOf<T>().apply { addAll(theComponents) }

    override fun getComponents(): List<T> = components

    override fun addComponent(component: T) {
        components.add(component)
    }

    override fun clear() {
        components.clear()
    }
}