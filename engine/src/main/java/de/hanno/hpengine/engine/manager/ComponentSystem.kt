package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

interface ComponentSystem<T : Component> {
    suspend fun update(scene: Scene, deltaSeconds: Float) {
        for(component in components) {
            component.update(scene, deltaSeconds)
        }
    }

    val components: List<T>
    fun addComponent(component: T)
    fun clear() { }
    fun extract(renderState: RenderState) {}

    fun onEntityAdded(entities: List<Entity>): MutableList<Component> {
        val matchedComponents = mutableSetOf<Component>()
        for (entity in entities) {
            matchedComponents.addAll(addCorrespondingComponents(entity.components.toList()))
        }
        logger.debug("${matchedComponents.size} components matched")
        return matchedComponents.toMutableList()
    }


    fun onComponentAdded(component: Component) {
        addCorrespondingComponents(listOf(component))
    }

    fun addCorrespondingComponents(components: List<Component>): List<Component> {
        val correspondingComponents = components.filter { componentClass.isAssignableFrom(it.javaClass) }

        logger.debug("${correspondingComponents.size} components corresponding")
        correspondingComponents.forEach { component -> addComponent(componentClass.cast(component)) }
        return correspondingComponents
    }

    val componentClass: Class<T>
    val logger: Logger
        get() = defaultLogger

    companion object {
        val defaultLogger = LogManager.getLogger(ComponentSystem::class)
    }
}

open class SimpleComponentSystem<T: Component>(override val componentClass: Class<T>, theComponents: List<T> = emptyList()) : ComponentSystem<T> {
    override val logger: Logger = LogManager.getLogger(this.javaClass)
    private val _components = mutableListOf<T>().apply { addAll(theComponents) }

    override val components: List<T>
        get() = _components

    override fun addComponent(component: T) {
        _components.add(component)
        logger.debug("Added component $component")
    }

    override fun clear() {
        _components.clear()
    }
}
