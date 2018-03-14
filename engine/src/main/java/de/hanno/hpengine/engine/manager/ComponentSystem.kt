package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity

interface ComponentSystem<T : Component> {
    fun update(deltaSeconds: Float) {
        getComponents().forEach {
            it.update(deltaSeconds)
        }
    }
    fun getComponents(): List<T>
    fun create(entity: Entity): T
    fun addComponent(component: T)
    fun clear()
    fun onSceneSet() {
        clear()
    }
    fun onEntityAdded(entities: List<Entity>) {
        entities.forEach {
            addCorrespondingComponents(it.components)
        }
    }

    fun addCorrespondingComponents(components: Map<Class<Component>, Component>) {
        components.map {
            val isAnonymousCLass = it.key.enclosingClass != null
            if (isAnonymousCLass) Pair(it.key.superclass, it.value) else Pair(it.key::class.java, it.value)
        }.filter { componentClass == it.first }.forEach { addComponent(it.second as T) }
    }

    val componentClass: Class<T>
}

open class SimpleComponentSystem<T: Component>(theComponentClass: Class<T>, theComponents: List<T> = emptyList(), val factory: (Entity) -> T) : ComponentSystem<T> {
    private val components = mutableListOf<T>().apply { addAll(theComponents) }

    override fun getComponents(): List<T> = components

    override fun create(entity: Entity) = factory(entity)

    override fun addComponent(component: T) {
        components.add(component)
    }

    override fun clear() {
        components.clear()
    }

    override val componentClass: Class<T> = theComponentClass

}