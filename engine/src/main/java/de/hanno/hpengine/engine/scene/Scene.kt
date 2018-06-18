package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
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
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
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
    val name: String
    var activeCamera: Camera
    var currentCycle: Long
    val managers: ManagerRegistry
    val entityManager: EntityManager
    val entitySystems: EntitySystemRegistry
    val componentSystems: ComponentSystemRegistry
    val renderSystems: List<RenderSystem>

    val materialManager: MaterialManager
    val environmentProbeManager: EnvironmentProbeManager //TODO: Remove this from interface

    var isInitiallyDrawn: Boolean
    val minMax: AABB

    fun clear() {
        componentSystems.clearSystems()
        entitySystems.clearSystems()
        entityManager.clear()
    }
    fun extract(currentWriteState: RenderState)

    fun getEntities() = entityManager.getEntities()
    fun addAll(entities: List<Entity>)

    fun getPointLights(): List<PointLight> = componentSystems.get(PointLightComponentSystem::class.java).getComponents()
    fun getTubeLights(): List<TubeLight> = componentSystems.get(TubeLightComponentSystem::class.java).getComponents()
    fun getAreaLights(): List<AreaLight> = componentSystems.get(AreaLightComponentSystem::class.java).getComponents()
    fun getAreaLightSystem(): AreaLightSystem = entitySystems.get(AreaLightSystem::class.java)
    fun getPointLightSystem(): PointLightSystem = entitySystems.get(PointLightSystem::class.java)
    fun write()
    fun write(name: String): Boolean
    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }
    fun restoreWorldCamera()
    override fun update(deltaSeconds: Float) {
        managers.update(deltaSeconds)
        componentSystems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        managers.afterUpdate()
    }
}

class Scene @JvmOverloads constructor(override val name: String = "new-scene-" + System.currentTimeMillis(), val engine: Engine) : IScene {
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

    val directionalLight = Entity("DirectionalLight")
            .apply { addComponent(DirectionalLight(this)) }
            .apply { addComponent(DirectionalLight.DirectionalLightController(engine, this)) }
            .apply { this@Scene.add(this) }

    private val camera = Entity("MainCamera")
            .apply { addComponent(inputComponentSystem.create(this)) }
            .apply { addComponent(cameraComponentSystem.create(this)) }
            .apply { this@Scene.add(this) }

    override var activeCamera: Camera = camera.getComponent(Camera::class.java)


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

        modelComponentSystem.allocateVertexIndexBufferSpace(entities) // TODO: This should go to onEntityAdded

        entitySystems.onEntityAdded(entities)
        componentSystems.onEntityAdded(entities)
        managers.onEntityAdded(entities)

        minMax.calculateMinMax(entityManager.getEntities())

        entityManager.entityAddedInCycle = currentCycle
        engine.eventBus.post(MaterialAddedEvent())
        engine.eventBus.post(EntityAddedEvent())
    }

    override fun update(deltaSeconds: Float) {
        managers.update(deltaSeconds)
        componentSystems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        entityManager.update(deltaSeconds)
        entityManager.afterUpdate()

    }

    override fun getEntities(): List<Entity> {
        return entityManager.getEntities()
    }

    override fun restoreWorldCamera() {
        activeCamera = camera.getComponent(Camera::class.java)
    }
    override fun extract(currentWriteState: RenderState) {
        batchingSystem.addRenderBatches(currentWriteState)
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmStatic val directory = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
