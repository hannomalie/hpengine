package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.calculateAABB
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.component.getScopeId
import org.koin.core.component.inject
import org.koin.core.component.newScope
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

fun scene(name: String, engineContext: EngineContext, block: SceneSyntax.() -> Unit): Scene = SceneSyntax(Scene(name, engineContext)).run {
    block()
    return scene
}

fun Engine.scene(name: String, block: SceneSyntax.() -> Unit): Scene = scene(name, engineContext) {
//    baseExtensions.materialExtension.manager.registerMaterials(sceneManager.scene.baseExtensions.materialExtension.manager.materials)
    engineContext.extensions.modelComponentExtension.manager.modelCache.putAll(engineContext.extensions.modelComponentExtension.manager.modelCache)
    block()
}

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(),
                                      val engineContext: EngineContext) : Updatable, KoinScopeComponent {
    override val scope: Scope = createScope()

    private val baseExtensions: BaseExtensions = engineContext.extensions

    var currentCycle: Long = 0
    val aabb = AABB(Vector3f(), 50f)

    val componentSystems: List<ComponentSystem<*>>
        get() = scope.getAll()

    val managers: List<Manager>
        get() = scope.getAll()

    val entitySystems: List<EntitySystem>
        get() = scope.getAll()

    val entityManager: EntityManager
        get() = scope.get()

    val extensions = baseExtensions

    fun restoreWorldCamera() {
        baseExtensions.cameraExtension.activeCamera = cameraEntity.getComponent(Camera::class.java)!!
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

    fun getEntities() = entityManager.getEntities()
    fun addAll(entities: List<Entity>) {
        entityManager.add(entities)

        entitySystems.forEach { it.onEntityAdded(this@Scene, entities) }
        componentSystems.forEach { it.onEntityAdded(entities) }
        managers.forEach { it.onEntityAdded(entities) }

        calculateBoundingVolume()

        entityManager.entityAddedInCycle = currentCycle
    }

    fun onComponentAdded(component: Component) {

        componentSystems.forEach { it.onComponentAdded(component) }
        managers.forEach { it.onComponentAdded(component) }
        entitySystems.forEach { it.onComponentAdded(this@Scene, component) }

        entityManager.componentAddedInCycle = currentCycle
    }

    val pointLights
        get() = baseExtensions.pointLightExtension.componentSystem.components

    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Entity? = entityManager.getEntities().find { e -> e.name == name }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        managers.forEach { it.update(scene, deltaSeconds) }
        componentSystems.forEach { it.update(scene, deltaSeconds) }
        entitySystems.forEach { it.update(scene, deltaSeconds) }
    }

    fun addComponent(selection: Entity, component: Component) {
        selection.addComponent(component)
        onComponentAdded(component)
    }

    fun calculateBoundingVolume() {
        aabb.localAABB = entityManager.getEntities().calculateAABB()
    }

    fun entity(name: String, block: Entity.() -> Unit): Entity = Entity(name).apply {
        block()
        add(this)
    }

}
