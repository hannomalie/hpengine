package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.extensibleDeferredRenderer
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.Mesh.Companion.IDENTITY
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.calculateAABB
import org.joml.Vector3f

class SceneSyntax(val scene: Scene) {
    val baseExtensions
        get() = scene.baseExtensions
    val extensions: List<Extension>
        get() = scene.extensions

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
    baseExtensions.modelComponentExtension.manager.modelCache.putAll(sceneManager.scene.baseExtensions.modelComponentExtension.manager.modelCache)
    block()
}

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(),
                                      val engineContext: EngineContext,
                                      val baseExtensions: BaseExtensions = BaseExtensions(engineContext),
                                      val nonBaseExtensions: List<Extension> = listOf(
                                            AmbientOcclusionExtension(engineContext),
                                            GiVolumeExtension(engineContext, baseExtensions.pointLightExtension.deferredRendererExtension),
                                            EnvironmentProbeExtension(engineContext)
                                      )): Updatable {
    var currentCycle: Long = 0
    var isInitiallyDrawn: Boolean = false
    val aabb = AABB(Vector3f(), 50f).apply {
        recalculate(IDENTITY)
    }

    val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    val managers: ManagerRegistry = SimpleManagerRegistry()
    val entitySystems = SimpleEntitySystemRegistry()

    val entityManager = EntityManager(
        baseExtensions.modelComponentExtension.componentSystem,
        baseExtensions.materialExtension.manager
    ).also { managers.register(it) }

    val extensions = (baseExtensions + engineContext.additionalExtensions + nonBaseExtensions)

    val materialManager = baseExtensions.materialExtension.manager
    val modelComponentManager = baseExtensions.modelComponentExtension.manager

    private val initiallyDrawnRenderSystem = object : RenderSystem {
        override fun render(result: DrawResult, renderState: RenderState) {
            isInitiallyDrawn = true
        }
    }
    init {
        extensions.forEach { extension ->
            extension.componentSystem?.let { componentSystems.register(it.componentClass as Class<Component>, it as ComponentSystem<Component>) }
            extension.entitySystem?.let { entitySystems.register(it) }
            extension.manager?.let { managers.register(it) }
        }

        extensions.forEach { extension ->
            extension.run {
                onInit()
            }
        }
    }

    fun afterSetScene() {
        register(extensions)
        engineContext.renderSystems.add(initiallyDrawnRenderSystem)
        engineContext.eventBus.register(this)
    }
    fun afterUnsetScene() {
        deregister(extensions)
        engineContext.renderSystems.remove(initiallyDrawnRenderSystem)
        engineContext.eventBus.unregister(this)
    }

    var activeCamera: Camera = cameraEntity.getComponent(Camera::class.java)!!

    fun restoreWorldCamera() {
        activeCamera = cameraEntity.getComponent(Camera::class.java)!!
    }

    fun extract(currentWriteState: RenderState) {
        currentWriteState.camera.init(activeCamera)
        currentWriteState.sceneInitiallyDrawn = isInitiallyDrawn
        currentWriteState.sceneMin.set(aabb.min)
        currentWriteState.sceneMax.set(aabb.max)

        for(system in componentSystems.getSystems()) {
            system.extract(currentWriteState)
        }
        for(system in entitySystems.systems) {
            system.extract(currentWriteState)
        }
        for(manager in managers.managers) {
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
//        engineContext.addResourceContext.locked {
            with(entityManager) { add(entities) }

            with(entitySystems) { onEntityAdded(this@Scene, entities) }
            with(componentSystems) { onEntityAdded(entities) }
            with(managers) { onEntityAdded(entities) }

            calculateBoundingVolume()
            engineContext.onEntityAdded(this, entities)

            entityManager.entityAddedInCycle = currentCycle
//        }
    }

    fun onComponentAdded(component: Component) {
        with(componentSystems) { onComponentAdded(component) }
        with(managers) { onComponentAdded(component) }
        with(entitySystems) { onComponentAdded(this@Scene, component) }

        entityManager.componentAddedInCycle = currentCycle
    }

    val pointLights
        get() = baseExtensions.pointLightExtension.componentSystem.getComponents()
    val tubeLights
        get() = baseExtensions.tubeLightExtension.componentSystem.getComponents()
    val areaLights: List<AreaLight>
        get() = baseExtensions.areaLightExtension.componentSystem.getComponents()

    fun getAreaLightSystem(): AreaLightSystem = entitySystems.get(AreaLightSystem::class.java)
    fun getPointLightSystem(): PointLightSystem = entitySystems.get(PointLightSystem::class.java)
    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Entity? {
        return entityManager.getEntities().find { e -> e.name == name }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        with(this@Scene.managers) {
            update(scene, deltaSeconds)
        }
        with(this@Scene.componentSystems) {
            update(scene, deltaSeconds)
        }
        with(this@Scene.entitySystems) {
            update(scene, deltaSeconds)
        }
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

fun Scene.register(extensions: List<Extension>) {
    extensions.forEach { extension ->
        extension.renderSystem?.let { engineContext.renderSystems.add(it) }
        extension.deferredRendererExtension?.let {
            engineContext.addResourceContext.launch {
                engineContext.backend.gpuContext {
                    engineContext.extensibleDeferredRenderer?.extensions?.add(it)
                }
            }
        }
        Unit
    }
}
fun Scene.deregister(extensions: List<Extension>) {
    extensions.forEach { extension ->
        extension.deferredRendererExtension?.let {
            engineContext.addResourceContext.launch {
                engineContext.backend.gpuContext {
                    engineContext.extensibleDeferredRenderer?.extensions?.remove(it)
                }
            }
        }
        Unit
    }
}
