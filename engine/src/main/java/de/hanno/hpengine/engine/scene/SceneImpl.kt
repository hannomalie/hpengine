package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.ScriptComponentSystem
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.component.CustomComponentSystem
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.GIVolumeSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.probe.ProbeSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.Mesh.Companion.IDENTITY
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.transform.AABB
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f

class SceneImpl @JvmOverloads constructor(override val name: String = "new-scene-" + System.currentTimeMillis(),
                                          val engine: EngineContext) : Scene {
    @Transient
    override var currentCycle: Long = 0
    @Transient
    override var isInitiallyDrawn: Boolean = false
    override val aabb = AABB(Vector3f(), 50f).apply {
        recalculate(IDENTITY)
    }

    override val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    override val managers: ManagerRegistry = SimpleManagerRegistry()
    override val materialManager = managers.register(MaterialManager(engine))
    override val entitySystems = SimpleEntitySystemRegistry()

    private val customComponentSystem = componentSystems.register(CustomComponentSystem())
    private val scriptComponentSystem = componentSystems.register(ScriptComponentSystem())
    private val clusterComponentSystem = componentSystems.register(ClustersComponentSystem())
    private val cameraComponentSystem = componentSystems.register(CameraComponentSystem(engine)).also {
        engine.renderSystems.add(it)
    }

    val giVolumeComponentSystem = componentSystems.register(SimpleComponentSystem(GIVolumeComponent::class.java))
    val giVolumeSystem = entitySystems.register(GIVolumeSystem(engine, this))

    private val inputComponentSystem = componentSystems.register(InputComponentSystem(engine))
    override val modelComponentSystem = componentSystems.register(ModelComponentSystem(engine, materialManager))
    val pointLightComponentSystem = componentSystems.register(PointLightComponentSystem())
    private val areaLightComponentSystem = componentSystems.register(AreaLightComponentSystem())
    private val tubeLightComponentSystem = componentSystems.register(TubeLightComponentSystem())

    override val entityManager = EntityManager(engine, engine.eventBus, this).apply { managers.register(this) }
    override val environmentProbeManager: EnvironmentProbeManager = EnvironmentProbeManager(engine).also {
        managers.register(it)
        engine.renderSystems.add(it)
    }

    val directionalLightSystem = DirectionalLightSystem(engine, this, engine.eventBus).apply {
        entitySystems.register(this)
        engine.renderSystems.add(this)
    }
    val pointLightSystemX = entitySystems.register(PointLightSystem(engine, this)).apply { engine.renderSystems.add(this) }
    private val areaLightSystemX = entitySystems.register(AreaLightSystem(engine, this)).apply { engine.renderSystems.add(this) }
    val probeSystem = entitySystems.register(ProbeSystem(this))
    //    TODO: Move this event/debug stuff outside of scene class
    val eventSystem = entitySystems.register(object : EntitySystem {
        override fun clear() {}
        override fun CoroutineScope.update(deltaSeconds: Float) {}
        override fun gatherEntities() {}
        override fun onEntityAdded(entities: List<Entity>) {
            engine.eventBus.post(MaterialAddedEvent())
            engine.eventBus.post(EntityAddedEvent())
        }
    })

    val directionalLight = Entity("DirectionalLight").apply {
        addComponent(DirectionalLight(this))
        addComponent(DirectionalLight.DirectionalLightController(engine, this))
        engine.addResourceContext.locked {
            with(this@SceneImpl) { add(this@apply) }
        }
    }

    val cameraEntity = Entity("MainCamera").apply {
        addComponent(inputComponentSystem.create(this))
    }

    val globalGiGrid = Entity("GlobalGiGrid").apply {
        addComponent(GIVolumeComponent(this, engine.textureManager.createGIVolumeGrids(), Vector3f(100f)))
        engine.addResourceContext.locked {
            with(this@SceneImpl) { add(this@apply) }
        }
    }
    val secondGiGrid = Entity("SecondGiGrid").apply {
        transform.translation(Vector3f(0f,0f,50f))
        addComponent(GIVolumeComponent(this, engine.textureManager.createGIVolumeGrids(), Vector3f(30f)))
        engine.addResourceContext.locked {
            with(this@SceneImpl) { add(this@apply) }
        }
    }


    override val camera = cameraComponentSystem.create(cameraEntity)
            .apply { cameraEntity.addComponent(this) }
            .apply {
                engine.addResourceContext.locked {
                    with(this@SceneImpl) { add(cameraEntity) }
                }
            }
//    TODO: Exclude from entity movement determination

    override var activeCamera: Camera = cameraEntity.getComponent(Camera::class.java)!!


    override var initialized: Boolean = false

    init {
        engine.renderSystems.add(object : RenderSystem {
            override fun render(result: DrawResult, state: RenderState) {
                isInitiallyDrawn = true
            }
        })
        engine.eventBus.register(this)

        initialized = true
    }

    override fun restoreWorldCamera() {
        activeCamera = cameraEntity.getComponent(Camera::class.java)!!
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState.camera.init(activeCamera)
        currentWriteState.sceneInitiallyDrawn = isInitiallyDrawn
        currentWriteState.sceneInitialized = initialized
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
}
