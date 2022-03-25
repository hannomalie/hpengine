package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.calculateAABB
import org.joml.Vector3f
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.component.get
import org.koin.core.scope.Scope

class SceneSyntax(val scene: Scene) {
    fun entity(name: String, block: Entity.() -> Unit) {
        scene.entity(name, block)
    }

    fun entities(block: EntitiesSyntax.() -> Unit) = EntitiesSyntax().run {
        block()
        scene.addAll(entities)
    }
}

class EntitiesSyntax {
    internal val entities = mutableListOf<Entity>()
    fun entity(name: String, block: Entity.() -> Unit): Entity = Entity(name).apply(block).apply { entities.add(this) }
}

fun scene(name: String, block: SceneSyntax.() -> Unit): Scene = SceneSyntax(Scene(name)).run {
    block()
    return scene
}

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis()) : Updatable, KoinScopeComponent {
    override val scope: Scope by lazy { createScope() }

    var currentCycle: Long = 0
    val aabb = AABB(Vector3f(), 50f)

    val componentSystems: List<ComponentSystem<*>>
        get() = scope.getAll<ComponentSystem<*>>().distinct()

    val managers: List<Manager>
        get() = scope.getAll<Manager>().distinct()

    val entitySystems: List<EntitySystem>
        get() = scope.getAll<EntitySystem>().distinct()

    val entityManager: EntityManager
        get() = scope.get()

    fun restoreWorldCamera() {
//        get<CameraExtension>().run { activeCameraEntity = cameraEntity }
    }

    init {
        entitySystems.forEach { it.gatherEntities(this) }

        componentSystems.forEach {
            it.onEntityAdded(getEntities())
        }
        managers.forEach { it.beforeSetScene(this) }
    }
    fun extract(currentWriteState: RenderState) {
        currentWriteState.sceneMin.set(aabb.min)
        currentWriteState.sceneMax.set(aabb.max)

        for (system in componentSystems) {
            system.extract(currentWriteState)
        }
        for (system in entitySystems) {
            system.extract(currentWriteState)
        }
        for (manager in managers) {
            manager.extract(this, currentWriteState)
        }
    }
    override fun toString() = name

    fun clear() {
        scope.close()
    }

    fun getEntities() = entityManager.entities
    fun addAll(entities: List<Entity>) = get<AddResourceContext>().launch {
        entityManager.add(entities)

        entitySystems.forEach { it.onEntityAdded(this@Scene, entities) }
        componentSystems.forEach { it.onEntityAdded(entities) }
        managers.forEach { it.onEntityAdded(entities) }

        calculateBoundingVolume()

        entityManager.entityAddedInCycle = currentCycle
    }

    fun onComponentAdded(component: Component) = get<AddResourceContext>().launch {

        componentSystems.forEach { it.onComponentAdded(component) }
        managers.forEach { it.onComponentAdded(component) }
        entitySystems.forEach { it.onComponentAdded(this@Scene, component) }

        entityManager.componentAddedInCycle = currentCycle
    }

    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Entity? = entityManager.entities.find { e -> e.name == name }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        managers.filterNot { it is SceneManager }.forEach { it.update(scene, deltaSeconds) }
        componentSystems.forEach { it.update(scene, deltaSeconds) }
        entitySystems.forEach { it.update(scene, deltaSeconds) }
    }

    fun addComponent(selection: Entity, component: Component) {
        selection.addComponent(component)
        onComponentAdded(component)
    }

    fun calculateBoundingVolume() {
        aabb.localAABB = entityManager.entities.calculateAABB()
    }

    fun entity(name: String, block: Entity.() -> Unit): Entity = newEntity(name, block).apply {
        add(this)
    }

}

fun newEntity(name: String, block: Entity.() -> Unit): Entity = Entity(name).apply {
    block()
}