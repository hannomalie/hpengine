package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface ComponentSystem<T : Component> {
    suspend fun update(scene: Scene, deltaSeconds: Float) {
        for(component in components) {
            component.update(scene, deltaSeconds)
        }
    }

    val components: List<T>
    fun addComponent(component: T)
    fun addComponents(components: List<T>) = components.forEach(::addComponent)

    fun clear() { }
    fun extract(renderState: RenderState) {}

    fun onEntityAdded(entities: List<Entity>): MutableList<Component> {
        val correspondingComponents = entities.flatMap { it.components.filterForCorresponding() }

        addComponents(correspondingComponents)

        return correspondingComponents.toMutableList()
    }


    fun onComponentAdded(component: Component) {
        addComponents(listOf(component).filterForCorresponding())
    }

    fun List<Component>.filterForCorresponding(): List<T> = filter { it.isCorresponding() } as List<T>

    fun Component.isCorresponding() = componentClass.isAssignableFrom(javaClass)

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
