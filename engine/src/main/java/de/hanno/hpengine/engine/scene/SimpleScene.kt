package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.entity.*
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.probe.ProbeSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.util.script.ScriptManager
import org.joml.Vector3f

class SimpleScene @JvmOverloads constructor(override val name: String = "new-simpleScene-" + System.currentTimeMillis(), val engine: Engine) : Scene {
    @Transient override var currentCycle: Long = 0
    @Transient override var isInitiallyDrawn: Boolean = false
    override val minMax = AABB(Vector3f(), 100f)

    override val renderSystems = mutableListOf<RenderSystem>()
    override val componentSystems: ComponentSystemRegistry = ComponentSystemRegistry()
    override val managers: ManagerRegistry = SimpleManagerRegistry()
    override val entitySystems = SimpleEntitySystemRegistry()

    private val clusterComponentSystem = componentSystems.register(ClustersComponentSystem(engine))
    private val cameraComponentSystem = componentSystems.register(CameraComponentSystem(engine))
    private val inputComponentSystem = componentSystems.register(InputComponentSystem(engine))
    private val modelComponentSystem = componentSystems.register(ModelComponentSystem(engine))
    private val pointLightComponentSystem = componentSystems.register(PointLightComponentSystem())
    private val areaLightComponentSystem = componentSystems.register(AreaLightComponentSystem())
    private val tubeLightComponentSystem = componentSystems.register(TubeLightComponentSystem())

    override val entityManager = EntityManager(engine, engine.eventBus).apply { managers.register(this) }
    override val environmentProbeManager = engine.environmentProbeManager.apply { renderSystems.add(this) }
    override val materialManager = managers.register(MaterialManager(engine, engine.textureManager))
    val scriptManager = managers.register(ScriptManager().apply { defineGlobals(engine, entityManager, materialManager) })

    val directionalLightSystem = entitySystems.register(DirectionalLightSystem(engine, this, engine.eventBus))
    val batchingSystem = entitySystems.register(BatchingSystem(engine, this))
    val pointLightSystemX = entitySystems.register(PointLightSystem(engine, this)).apply { renderSystems.add(this) }
    private val areaLightSystemX = entitySystems.register(AreaLightSystem(engine, this)).apply { renderSystems.add(this) }
    val probeSystem = entitySystems.register(ProbeSystem(engine, this))
//    TODO: Move this event/debug stuff outside of simpleScene class
    val eventSystem = entitySystems.register(object: EntitySystem {
        override fun clear() { }
        override fun update(deltaSeconds: Float) { }
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


    override fun init(engine: Engine) {
        engine.eventBus.register(this)
    }

    override fun restoreWorldCamera() {
        activeCamera = cameraEntity.getComponent(Camera::class.java)
    }
    override fun extract(currentWriteState: RenderState) {
        with(entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()) {
            currentWriteState.init(engine.renderManager.vertexIndexBufferStatic, engine.renderManager.vertexIndexBufferAnimated, componentSystems.get(de.hanno.hpengine.engine.model.ModelComponentSystem::class.java).joints, activeCamera, entityManager.entityMovedInCycle, entitySystems.get(de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem::class.java).directionalLightMovedInCycle, entitySystems.get(de.hanno.hpengine.engine.graphics.light.point.PointLightSystem::class.java).pointLightMovedInCycle, isInitiallyDrawn, minMax.min, minMax.max, currentWriteState.cycle, viewMatrixAsBuffer, projectionMatrixAsBuffer, viewProjectionMatrixAsBuffer, scatterFactor, direction, color, entityManager.entityAddedInCycle)
        }
        batchingSystem.addRenderBatches(currentWriteState)
        modelComponentSystem.copyGpuBuffers(currentWriteState)
        areaLightSystemX.copyGpuBuffers(currentWriteState)
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmStatic val directory = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
