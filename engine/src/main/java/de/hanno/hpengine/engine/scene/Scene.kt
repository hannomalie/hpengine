package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.extensibleDeferredRenderer
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableInputComponent
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
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
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.calculateAABB
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.joml.Vector3f
import java.util.Optional

class SkyBox(var cubeMap: CubeMap)

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
    baseExtensions.materialExtension.manager.addMaterials(sceneManager.scene.baseExtensions.materialExtension.manager.materials)
    baseExtensions.modelComponentExtension.manager.modelCache.putAll(sceneManager.scene.baseExtensions.modelComponentExtension.manager.modelCache)
    block()
}

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(),
                                      val engineContext: EngineContext,
                                      val nonBaseExtensions: List<Extension> = listOf(
                                            GiVolumeExtension(engineContext),
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
    val entityManager = EntityManager(this).also { managers.register(it) }

    val baseExtensions = BaseExtensions(engineContext)
    val extensions = (baseExtensions + engineContext.additionalExtensions + nonBaseExtensions).also {
        register(it)
    }

    val materialManager = baseExtensions.materialExtension.manager
    val modelComponentManager = baseExtensions.modelComponentExtension.manager

    val directionalLight = entity("DirectionalLight") {
        addComponent(DirectionalLight(this))
        addComponent(DirectionalLight.DirectionalLightController(engineContext, this))
    }

    val cameraEntity = entity("MainCamera") {
        addComponent(MovableInputComponent(engineContext, this))
        addComponent(baseExtensions.cameraExtension.componentSystem.create(this))
    }

    val camera = cameraEntity.getComponent(Camera::class.java)!!
    var activeCamera: Camera = cameraEntity.getComponent(Camera::class.java)!!

    init {
        engineContext.renderSystems.add(object : RenderSystem {
            override fun render(result: DrawResult, state: RenderState) {
                isInitiallyDrawn = true
            }
        })
        engineContext.eventBus.register(this)
    }

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
        engineContext.addResourceContext.locked {
            with(entityManager) { add(entities) }

            with(entitySystems) { onEntityAdded(this@Scene, entities) }
            with(componentSystems) { onEntityAdded(entities) }
            with(managers) { onEntityAdded(entities) }

            calculateBoundingVolume()
            engineContext.onEntityAdded(this, entities)

            // TODO: This is not too correct but the cycle counter gets updated just before this happens
            entityManager.entityAddedInCycle = currentCycle
        }
    }

    fun onComponentAdded(component: Component) {
        with(componentSystems) { onComponentAdded(component) }
        with(managers) { onComponentAdded(component) }
        with(entitySystems) { onComponentAdded(this@Scene, component) }

        // TODO: This is not too correct but the cycle counter gets updated just before this happens
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
    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        with(managers) {
            update(scene, deltaSeconds)
        }
        with(componentSystems) {
            update(scene, deltaSeconds)
        }
        with(entitySystems) {
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
        extension.componentSystem?.let { componentSystems.register(it.componentClass as Class<Component>, it as ComponentSystem<Component>) }
        extension.entitySystem?.let { entitySystems.register(it) }
        extension.renderSystem?.let { engineContext.renderSystems.add(it) }
        extension.manager?.let { managers.register(it) }
        extension.deferredRendererExtension?.let {
            engineContext.addResourceContext.locked {
                engineContext.backend.gpuContext {
                    engineContext.extensibleDeferredRenderer?.extensions?.add(it)
                }
            }
        }
    }
}
