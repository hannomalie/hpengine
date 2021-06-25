package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.calculateAABB
import org.joml.Vector3f

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
                                      val engineContext: EngineContext) : Updatable {

    private val baseExtensions: BaseExtensions = engineContext.extensions

    var currentCycle: Long = 0
    val aabb = AABB(Vector3f(), 50f)

    val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    val managers: ManagerRegistry = SimpleManagerRegistry()
    val entitySystems = SimpleEntitySystemRegistry()

    val entityManager = EntityManager().also { managers.register(it) }

    val extensions = baseExtensions

    init {
        engineContext.extensions.forEach { extension ->
            extension.componentSystem?.let {
                componentSystems.register(it.componentClass as Class<Component>, it as ComponentSystem<Component>)
            }
            extension.entitySystem?.let { entitySystems.register(it) }
            extension.manager?.let { managers.register(it) }
        }
    }


    fun restoreWorldCamera() {
        baseExtensions.cameraExtension.activeCamera = cameraEntity.getComponent(Camera::class.java)!!
    }

    fun extract(currentWriteState: RenderState) {
        currentWriteState.sceneMin.set(aabb.min)
        currentWriteState.sceneMax.set(aabb.max)

        for (system in componentSystems.getSystems()) {
            system.extract(currentWriteState)
        }
        for (system in entitySystems.systems) {
            system.extract(currentWriteState)
        }
        for (manager in managers.managers) {
            manager.value.extract(this, currentWriteState)
        }
    }

    override fun toString() = name

    fun clear() {
        componentSystems.clearSystems()
        entitySystems.clearSystems()
        entityManager.clear()
    }

    fun getEntities() = entityManager.getEntities()
    fun addAll(entities: List<Entity>) {
        with(entityManager) { add(entities) }
        entityManager.cacheEntityIndices(this)

        with(entitySystems) { onEntityAdded(this@Scene, entities) }
        with(componentSystems) { onEntityAdded(entities) }
        with(managers) { onEntityAdded(entities) }

        calculateBoundingVolume()

        entityManager.entityAddedInCycle = currentCycle
    }

    fun onComponentAdded(component: Component) {
        with(componentSystems) { onComponentAdded(component) }
        with(managers) { onComponentAdded(component) }
        with(entitySystems) { onComponentAdded(this@Scene, component) }

        entityManager.componentAddedInCycle = currentCycle
    }

    val pointLights
        get() = baseExtensions.pointLightExtension.componentSystem.components
    val tubeLights
        get() = baseExtensions.tubeLightExtension.componentSystem.components
    val areaLights: List<AreaLight>
        get() = baseExtensions.areaLightExtension.componentSystem.components

    fun getAreaLightSystem(): AreaLightSystem = entitySystems.get(AreaLightSystem::class.java)
    fun getPointLightSystem(): PointLightSystem = entitySystems.get(PointLightSystem::class.java)
    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Entity? = entityManager.getEntities().find { e -> e.name == name }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        managers.update(scene, deltaSeconds)
        componentSystems.update(scene, deltaSeconds)
        entitySystems.update(scene, deltaSeconds)
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
