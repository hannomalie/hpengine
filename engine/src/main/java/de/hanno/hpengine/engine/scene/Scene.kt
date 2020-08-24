package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.probe.ProbeSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
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
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector3f
import java.io.Serializable
import java.util.Optional

class SkyBox(var cubeMap: CubeMap)

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(),
                                      val engineContext: EngineContext): Updatable, Serializable {
    var currentCycle: Long = 0
    var isInitiallyDrawn: Boolean = false
    val aabb = AABB(Vector3f(), 50f).apply {
        recalculate(IDENTITY)
    }

    val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    val managers: ManagerRegistry = SimpleManagerRegistry()
    val entitySystems = SimpleEntitySystemRegistry()
    val entityManager = EntityManager(engineContext, engineContext.eventBus, this).apply { managers.register(this) }

    private val baseExtensions = BaseExtensions(engineContext)
    val extensions = (baseExtensions + listOf(
        GiVolumeExtension(engineContext),
        EnvironmentProbeExtension(engineContext)
    )).apply { register() }

    val materialManager = baseExtensions.materialExtension.manager

    private val cameraComponentSystem = extensions.firstIsInstance<CameraExtension>().componentSystem

    val giVolumeSystem = extensions.firstIsInstance<GiVolumeExtension>().entitySystem

    private val inputComponentSystem = baseExtensions.inputComponentExtension.componentSystem
    val modelComponentSystem = baseExtensions.modelComponentExtension.componentSystem

    val environmentProbeManager = extensions.firstIsInstance<EnvironmentProbeExtension>().manager

    //    TODO: Move this event/debug stuff outside of scene class
    val eventSystem = entitySystems.register(object : EntitySystem {
        override fun clear() {}
        override fun gatherEntities(scene: Scene) {}
        override fun onEntityAdded(scene: Scene, entities: List<Entity>) {
            engineContext.eventBus.post(MaterialAddedEvent())
            engineContext.eventBus.post(EntityAddedEvent())
        }
    })

    val directionalLight = Entity("DirectionalLight").apply {
        addComponent(DirectionalLight(this))
        addComponent(DirectionalLight.DirectionalLightController(engineContext, this))
        engineContext.addResourceContext.locked {
            with(this@Scene) { add(this@apply) }
        }
    }

    val cameraEntity = Entity("MainCamera").apply {
        addComponent(inputComponentSystem.create(this))
    }

    val globalGiGrid = Entity("GlobalGiGrid").apply {
        addComponent(GIVolumeComponent(this, engineContext.textureManager.createGIVolumeGrids(), Vector3f(100f)))
        engineContext.addResourceContext.locked {
            with(this@Scene) { add(this@apply) }
        }
    }
    val secondGiGrid = Entity("SecondGiGrid").apply {
        transform.translation(Vector3f(0f,0f,50f))
        addComponent(GIVolumeComponent(this, engineContext.textureManager.createGIVolumeGrids(), Vector3f(30f)))
        engineContext.addResourceContext.locked {
            with(this@Scene) { add(this@apply) }
        }
    }


    val camera = cameraComponentSystem.create(cameraEntity)
            .apply { cameraEntity.addComponent(this) }
            .apply {
                engineContext.addResourceContext.locked {
                    with(this@Scene) { add(cameraEntity) }
                }
            }
//    TODO: Exclude from entity movement determination

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
        with(entityManager) { add(entities) }

        with(entitySystems) { onEntityAdded(this@Scene, entities) }
        with(componentSystems) { onEntityAdded(entities) }
        with(managers) { onEntityAdded(entities) }

        calculateBoundingVolume()

        // TODO: This is not too correct but the cycle counter gets updated just before this happens
        entityManager.entityAddedInCycle = currentCycle
    }

    fun onComponentAdded(component: Component) {
        with(componentSystems) { onComponentAdded(component) }
        with(managers) { onComponentAdded(component) }
        with(entitySystems) { onComponentAdded(this@Scene, component) }

        // TODO: This is not too correct but the cycle counter gets updated just before this happens
        entityManager.componentAddedInCycle = currentCycle
    }

    fun getPointLights(): List<PointLight> = componentSystems.get(PointLightComponentSystem::class.java).getComponents()
    fun getTubeLights(): List<TubeLight> = componentSystems.get(TubeLightComponentSystem::class.java).getComponents()
    fun getAreaLights(): List<AreaLight> = componentSystems.get(AreaLightComponentSystem::class.java).getComponents()
    fun getAreaLightSystem(): AreaLightSystem = entitySystems.get(AreaLightSystem::class.java)
    fun getPointLightSystem(): PointLightSystem = entitySystems.get(PointLightSystem::class.java)
    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }

    private fun List<Extension>.register() {
        forEach { extension ->
            extension.componentSystem?.let { componentSystems.register(it.componentClass as Class<Component>, it as ComponentSystem<Component>) }
            extension.entitySystem?.let { entitySystems.register(it) }
            extension.renderSystem?.let { engineContext.renderSystems.add(it) }
            extension.manager?.let { }
        }
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
}
