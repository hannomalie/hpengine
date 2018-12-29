package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.event.MeshSelectedEvent
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.probe.ProbeSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.util.script.ScriptManager
import net.engio.mbassy.listener.Handler
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class SimpleScene @JvmOverloads constructor(override val name: String = "new-simpleScene-" + System.currentTimeMillis(), val engine: Engine) : Scene {
    @Transient
    override var currentCycle: Long = 0
    @Transient
    override var isInitiallyDrawn: Boolean = false
    override val minMax = AABB(Vector3f(), 100f)

    override val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    override val managers: ManagerRegistry = SimpleManagerRegistry()
    override val entitySystems = SimpleEntitySystemRegistry()

    private val clusterComponentSystem = componentSystems.register(ClustersComponentSystem(engine))
    private val cameraComponentSystem = componentSystems.register(CameraComponentSystem(engine)).also {
        engine.renderSystems.add(it)
    }
    private val inputComponentSystem = componentSystems.register(InputComponentSystem(engine))
    private val modelComponentSystem = componentSystems.register(ModelComponentSystem(engine))
    private val pointLightComponentSystem = componentSystems.register(PointLightComponentSystem())
    private val areaLightComponentSystem = componentSystems.register(AreaLightComponentSystem())
    private val tubeLightComponentSystem = componentSystems.register(TubeLightComponentSystem())

    override val entityManager = EntityManager(engine, engine.eventBus, this).apply { managers.register(this) }
    override val environmentProbeManager: EnvironmentProbeManager = EnvironmentProbeManager(engine, engine.renderManager.renderer).also {
        managers.register(it)
        engine.renderSystems.add(it)
    }
    override val materialManager = managers.register(MaterialManager(engine, engine.textureManager))
    val scriptManager = managers.register(ScriptManager().apply { defineGlobals(engine, entityManager, materialManager) })

    val directionalLightSystem = entitySystems.register(DirectionalLightSystem(engine, this, engine.eventBus))
    val batchingSystem = entitySystems.register(BatchingSystem(engine, this))
    val pointLightSystemX = entitySystems.register(PointLightSystem(engine, this)).apply { engine.renderSystems.add(this) }
    private val areaLightSystemX = entitySystems.register(AreaLightSystem(engine, this)).apply { engine.renderSystems.add(this) }
    val probeSystem = entitySystems.register(ProbeSystem(engine, this))
    //    TODO: Move this event/debug stuff outside of simpleScene class
    val eventSystem = entitySystems.register(object : EntitySystem {
        override fun clear() {}
        override fun update(deltaSeconds: Float) {}
        override fun gatherEntities() {}
        override fun onEntityAdded(entities: List<Entity>) {
            engine.eventBus.post(MaterialAddedEvent())
            engine.eventBus.post(EntityAddedEvent())
        }
    })

    val directionalLight = Entity("DirectionalLight")
            .apply { addComponent(DirectionalLight(this)) }
            .apply { addComponent(DirectionalLight.DirectionalLightController(engine, this)) }
            .apply { this@SimpleScene.add(this) }

    val cameraEntity = Entity("MainCamera")
            .apply { addComponent(inputComponentSystem.create(this)) }

    override val camera = cameraComponentSystem.create(cameraEntity)
            .apply { cameraEntity.addComponent(this) }
            .apply { this@SimpleScene.add(cameraEntity) }
//    TODO: Exclude from entity movement determination

    override var activeCamera: Camera = cameraEntity.getComponent(Camera::class.java)


    init {
        engine.renderSystems.add(object : RenderSystem {
            override fun render(result: DrawResult, state: RenderState) {
                isInitiallyDrawn = true
            }
        })
        engine.eventBus.register(this)
    }

    override fun restoreWorldCamera() {
        activeCamera = cameraEntity.getComponent(Camera::class.java)
    }

    override fun extract(currentWriteState: RenderState) {
        with(entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()) {
            currentWriteState.init(vertexIndexBufferStatic = engine.renderManager.vertexIndexBufferStatic,
                    vertexIndexBufferAnimated = engine.renderManager.vertexIndexBufferAnimated,
                    joints = componentSystems.get(ModelComponentSystem::class.java).joints,
                    camera = activeCamera,
                    entityMovedInCycle = entityManager.entityMovedInCycle,
                    staticEntityMovedInCycle = entityManager.staticEntityMovedInCycle,
                    directionalLightHasMovedInCycle = entitySystems.get(DirectionalLightSystem::class.java).directionalLightMovedInCycle,
                    pointLightMovedInCycle = entitySystems.get(PointLightSystem::class.java).pointLightMovedInCycle,
                    sceneInitiallyDrawn = isInitiallyDrawn,
                    sceneMin = minMax.min,
                    sceneMax = minMax.max,
                    cycle = currentWriteState.cycle,
                    directionalLightViewMatrixAsBuffer = viewMatrixAsBuffer,
                    directionalLightProjectionMatrixAsBuffer = projectionMatrixAsBuffer,
                    directionalLightViewProjectionMatrixAsBuffer = viewProjectionMatrixAsBuffer,
                    directionalLightScatterFactor = scatterFactor,
                    directionalLightDirection = direction,
                    directionalLightColor = color,
                    entityAddedInCycle = entityManager.entityAddedInCycle,
                    environmentMapsArray0Id = -1,
                    environmentMapsArray3Id = -1,
                    activeProbeCount = 0,
                    environmentMapMin = BufferUtils.createFloatBuffer(1),
                    environmentMapMax = BufferUtils.createFloatBuffer(1),
                    environmentMapWeights = BufferUtils.createFloatBuffer(1),
                    skyBoxMaterialIndex = 0,
                    pointLights = emptyList(),
                    pointLightsBuffer = PersistentMappedBuffer(engine.gpuContext, 4),
                    areaLights = emptyList(),
                    tubeLights = emptyList(),
                    pointLightShadowMapStrategy = pointLightSystemX.shadowMapStrategy,
                    areaLightDepthMaps = ArrayList()
                    )
        }
//        TODO: Make this generic
        modelComponentSystem.extract(currentWriteState)
        batchingSystem.extract(currentWriteState)
        materialManager.extract(currentWriteState)
        environmentProbeManager.extract(currentWriteState)
        pointLightSystemX.extract(currentWriteState)
        areaLightSystemX.extract(currentWriteState)
    }

    @Handler
    fun handleSelection(event: MeshSelectedEvent) {
        getEntities().parallelStream().forEach { e -> e.isSelected = false }
        val entity = getEntities().get(event.entityIndex)
        val mesh = entity.getComponent(ModelComponent::class.java).meshes[event.meshIndex]
        entity.isSelected = true
    }
}
