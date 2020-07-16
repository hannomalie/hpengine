package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

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

//     Workaround for https://youtrack.jetbrains.com/issue/KT-11488?_ga=2.92346137.567661805.1573652933-1826229974.1518078622
    fun onEntityAdded(entities: List<Entity>): MutableMap<Class<out Component>, MutableList<Component>> {
        val matchedComponents = mutableMapOf<Class<out Component>, MutableList<Component>>()
        for (entity in entities) {
            val matched = addCorrespondingComponents(entity.components)
            matched.forEach {
                matchedComponents.putIfAbsent(it.key, mutableListOf())
                matchedComponents[it.key]!!.add(it.value)
            }
        }
        logger.debug("${matchedComponents.entries.flatMap { it.value }.size} components matched")
        return matchedComponents
    }


    fun onComponentAdded(component: Component) {
        addCorrespondingComponents(mapOf(component::class.java to component))
    }

    fun addCorrespondingComponents(components: Map<Class<out Component>, Component>): Map<Class<out Component>, Component> {
        val correspondingComponents = components.filter { it.key == componentClass || componentClass.isAssignableFrom(it.key)}

        logger.debug("${correspondingComponents.size} components corresponding")
        correspondingComponents.forEach { addComponent(componentClass.cast(it.value)) }
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
    private val components = mutableListOf<T>().apply { addAll(theComponents) }

    override fun getComponents(): List<T> = components

    override fun addComponent(component: T) {
        addComponentImpl(component)
    }

    // Workaroung for https://youtrack.jetbrains.com/issue/KT-11488?_ga=2.92346137.567661805.1573652933-1826229974.1518078622
    protected fun addComponentImpl(component: T) {
        components.add(component)
        logger.debug("Added component $component")
    }

    override fun clear() {
        components.clear()
    }
}
