package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystemRegistry
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.probe.ProbeSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleSystemsRegistry
import de.hanno.hpengine.engine.manager.SystemsRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.util.script.ScriptManager
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*

interface IScene : LifeCycle, Serializable {
    var activeCamera: Entity
    var currentCycle: Long
    val entityManager: EntityManager
    val entitySystems: EntitySystemRegistry
    val modelComponentSystem: ModelComponentSystem
    val camera: Entity
    fun clear()
    val directionalLightSystem: DirectionalLightSystem
    fun extract(currentWriteState: RenderState)
    fun entityMovedInCycle(): Long
    var entityAddedInCycle: Long
    fun pointLightMovedInCycle(): Long
    var isInitiallyDrawn: Boolean
    val minMax: AABB
    fun setEntityMovedInCycleToCurrentCycle()
    val renderStateConsumers: List<RenderSystem>
    fun getEntities(): List<Entity>
    val materialManager: MaterialManager
    fun addAll(entities: List<Entity>)
    val cameraComponentSystem: CameraComponentSystem
    val clusterComponentSystem: ClustersComponentSystem
    val environmentProbeManager: EnvironmentProbeManager
    fun getPointLights(): List<PointLight>
    fun getTubeLights(): List<TubeLight>
    fun getAreaLights(): List<AreaLight>
    val areaLightSystem: AreaLightSystem
    val pointLightSystem: PointLightSystem
    val name: String
    fun write(): Unit
    fun write(name: String): Boolean
    fun add(entity: Entity)
    val scriptManager: ScriptManager
    fun getEntity(name: String): Optional<Entity>
}

class Scene @JvmOverloads constructor(override val name: String = "new-scene-" + System.currentTimeMillis(), val engine: Engine) : IScene {
    override fun clear() {
        modelComponentSystem.clear()
    }

    @Transient private var entityMovedInCycle: Long = 0
    @Transient override var entityAddedInCycle: Long = 0
    @Transient override var currentCycle: Long = 0
    @Transient override var isInitiallyDrawn: Boolean = false
    override val minMax = AABB(Vector3f(), 100f)

    override val renderStateConsumers = mutableListOf<RenderSystem>()
    val componentSystems: SystemsRegistry = SimpleSystemsRegistry()
    val managers: ManagerRegistry = SimpleManagerRegistry()

    override val entityManager = (EntityManager(engine, engine.eventBus))
    override val environmentProbeManager = engine.environmentProbeManager.apply { renderStateConsumers.add(this) }

    override val clusterComponentSystem = componentSystems.register(ClustersComponentSystem(engine))
    override val cameraComponentSystem = componentSystems.register(CameraComponentSystem(engine))
    val inputComponentSystem = componentSystems.register(InputComponentSystem(engine))
    override val modelComponentSystem = componentSystems.register(ModelComponentSystem(engine))
    val pointLightComponentSystem = componentSystems.register(PointLightComponentSystem())
    val areaLightComponentSystem = componentSystems.register(AreaLightComponentSystem())
    val tubeLightComponentSystem = componentSystems.register(TubeLightComponentSystem())

    override val materialManager = managers.register(MaterialManager(engine, engine.textureManager))
    override val scriptManager = managers.register(ScriptManager().apply { defineGlobals(engine, entityManager, materialManager) })

    override val entitySystems = SimpleEntitySystemRegistry()
    override val directionalLightSystem = entitySystems.register(DirectionalLightSystem(engine, this, engine.eventBus))
    val batchingSystem = entitySystems.register(BatchingSystem(engine, this))
    override val pointLightSystem = entitySystems.register(PointLightSystem(engine, this)).apply { renderStateConsumers.add(this) }
    override val areaLightSystem = entitySystems.register(AreaLightSystem(engine, this)).apply { renderStateConsumers.add(this) }

    val probeSystem = entitySystems.register(ProbeSystem(engine, this))

    val directionalLight = Entity("DirectionalLight")
            .apply { addComponent(DirectionalLight(this)) }
            .apply { addComponent(DirectionalLight.DirectionalLightController(engine, this)) }
            .apply { this@Scene.add(this) }

    override val camera = Entity("MainCamera")
            .apply { addComponent(inputComponentSystem.create(this)) }
            .apply { addComponent(cameraComponentSystem.create(this)) }
            .apply { this@Scene.add(this) }

    override var activeCamera: Entity = camera

    override fun init(engine: Engine) {
        engine.eventBus.register(this)
    }

    override fun write() {
        write(name)
    }

    override fun write(name: String): Boolean {
        val fileName = FilenameUtils.getBaseName(name)
        var fos: FileOutputStream? = null
        var out: ObjectOutputStream? = null
        try {
            fos = FileOutputStream("$directory$fileName.hpscene")
            out = ObjectOutputStream(fos)
            out.writeObject(this)
            //			FSTObjectOutput newOut = new FSTObjectOutput(out);
            //			newOut.writeObject(this);
            //			newOut.close();

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                out!!.close()
                fos!!.close()
                return true
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return false
    }

    override fun addAll(entities: List<Entity>) {
        entityManager.add(entities)

        modelComponentSystem.allocateVertexIndexBufferSpace(entities)

        entitySystems.onEntityAdded(entities)
        componentSystems.onEntityAdded(entities)
        managers.onEntityAdded(entities)

        minMax.calculateMinMax(entityManager.getEntities())

        entityAddedInCycle = currentCycle
        engine.eventBus.post(MaterialAddedEvent())
        engine.eventBus.post(EntityAddedEvent())
    }

    override fun add(entity: Entity) = addAll(listOf(entity))

    override fun update(deltaSeconds: Float) {
        managers.update(deltaSeconds)

        componentSystems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        entityManager.update(deltaSeconds)
        entityManager.afterUpdate()

//        if (scene.entityMovedInCycle() == newDrawCycle) {
//            engine.eventBus.post(EntityMovedEvent()) TODO: Check if this is still necessary
//        }
//        if (directionalLightSystem.getDirectionalLight().entity.hasMoved()) {
//            engine.eventBus.post(DirectionalLightHasMovedEvent())
//        }
    }

    override fun getEntities(): List<Entity> {
        return entityManager.getEntities()
    }

    override fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }

    override fun getPointLights(): List<PointLight> = pointLightComponentSystem.getComponents()
    override fun getTubeLights(): List<TubeLight> = tubeLightComponentSystem.getComponents()
    override fun getAreaLights(): List<AreaLight> = areaLightComponentSystem.getComponents()

    override fun entityMovedInCycle() = entityMovedInCycle
    override fun pointLightMovedInCycle() = pointLightSystem.pointLightMovedInCycle

    override fun setEntityMovedInCycleToCurrentCycle() {
        entityMovedInCycle = currentCycle
    }

    override fun extract(currentWriteState: RenderState) {
        batchingSystem.addRenderBatches(currentWriteState)
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmStatic val directory = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
