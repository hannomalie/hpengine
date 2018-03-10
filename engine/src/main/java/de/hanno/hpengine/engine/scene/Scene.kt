package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.SimpleEntitySystemRegistry
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.light.AreaLight
import de.hanno.hpengine.engine.graphics.light.LightManager
import de.hanno.hpengine.engine.graphics.light.PointLight
import de.hanno.hpengine.engine.graphics.light.TubeLight
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.manager.SimpleSystemsRegistry
import de.hanno.hpengine.engine.manager.SystemsRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.util.script.ScriptManager
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import org.lwjgl.system.windows.POINTL
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Logger

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(), val engine: Engine, sceneManager: SceneManager = engine.sceneManager) : LifeCycle, Serializable {

    val systems: SystemsRegistry = SimpleSystemsRegistry()
    val entityManager = EntityManager(engine, engine.eventBus)
    val environmentProbeManager = engine.environmentProbeManager
    val clusterComponentSystem = systems.register(ClustersComponentSystem(engine))
    val cameraComponentSystem = systems.register(CameraComponentSystem(engine))
    val inputComponentSystem = systems.register(InputComponentSystem(engine))
    val modelComponentSystem = systems.register(ModelComponentSystem(engine))
    val materialManager = MaterialManager(engine, engine.textureManager)
    val lightManager = LightManager(engine, engine.eventBus, materialManager, sceneManager, engine.gpuContext, engine.programManager, inputComponentSystem, modelComponentSystem)
    val scriptManager = ScriptManager().apply { defineGlobals(engine, entityManager, materialManager) }

    val entitySystems = SimpleEntitySystemRegistry()
    val batchingSystem = entitySystems.register(BatchingSystem(engine, this))

    val camera = entityManager.create()
            .apply { addComponent(inputComponentSystem.create(this)) }
            .apply { addComponent(cameraComponentSystem.create(this)) }
            .apply { entityManager.add(this) }

    var activeCamera = camera

    private val probes = CopyOnWriteArrayList<ProbeData>()

    @Transient private var entityMovedInCycle: Long = 0
    @Transient var entityAddedInCycle: Long = 0
        private set
    @Transient
    var currentRenderCycle: Long = 0
    @Transient var isInitiallyDrawn: Boolean = false
    val minMax = AABB(Vector3f(), 100f)

    override fun init(engine: Engine) {
        engine.eventBus.register(this)
    }

    fun write() {
        write(name)
    }

    fun write(name: String): Boolean {
        val fileName = FilenameUtils.getBaseName(name)
        var fos: FileOutputStream? = null
        var out: ObjectOutputStream? = null
        try {
            fos = FileOutputStream("$directory$fileName.hpscene")
            out = ObjectOutputStream(fos)
            probes.clear()
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

    fun calculateMinMax() {
        calculateMinMax(entityManager.getEntities())
    }

    private fun calculateMinMax(entities: List<Entity>) {
        if (entities.isEmpty()) {
            minMax.min.set(-1f, -1f, -1f)
            minMax.max.set(1f, 1f, 1f)
            return
        }

        minMax.min.set(absoluteMaximum)
        minMax.max.set(absoluteMinimum)

        for (entity in entities) {
            val (currentMin, currentMax) = entity.minMaxWorld
            minMax.min.x = if (currentMin.x < minMax.min.x) currentMin.x else minMax.min.x
            minMax.min.y = if (currentMin.y < minMax.min.y) currentMin.y else minMax.min.y
            minMax.min.z = if (currentMin.z < minMax.min.z) currentMin.z else minMax.min.z

            minMax.max.x = if (currentMax.x > minMax.max.x) currentMax.x else minMax.max.x
            minMax.max.y = if (currentMax.y > minMax.max.y) currentMax.y else minMax.max.y
            minMax.max.z = if (currentMax.z > minMax.max.z) currentMax.z else minMax.max.z
        }
    }

    fun addAll(entities: List<Entity>) {
        entityManager.add(entities)
        modelComponentSystem.allocateVertexIndexBufferSpace(entities)
        calculateMinMax(entities)
        entityAddedInCycle = currentRenderCycle
        engine.eventBus.post(MaterialAddedEvent())
        engine.eventBus.post(EntityAddedEvent())
    }

    fun add(entity: Entity) = addAll(listOf(entity))

    override fun update(deltaSeconds: Float) {
        materialManager.update(deltaSeconds)
        lightManager.update(deltaSeconds, currentRenderCycle)

        systems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        entityManager.update(deltaSeconds)
    }

    fun getEntities(): List<Entity> {
        return entityManager.getEntities()
    }

    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }

    fun getPointLights(): List<PointLight> = lightManager.pointLights
    fun getTubeLights(): List<TubeLight> = lightManager.tubeLights
    fun getAreaLights(): List<AreaLight> = lightManager.areaLights
    fun addPointLight(pointLight: PointLight) = lightManager.addLight(pointLight)
    fun addTubeLight(tubeLight: TubeLight) = lightManager.addLight(tubeLight)
    fun entityMovedInCycle() = entityMovedInCycle
    fun pointLightMovedInCycle() = lightManager.pointLightMovedInCycle

    companion object {
        private const val serialVersionUID = 1L

        private val LOGGER = Logger.getLogger(Scene::class.java.name)

        private val absoluteMaximum = Vector3f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
        private val absoluteMinimum = Vector3f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

        @JvmStatic val directory: String
            get() = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }

    fun setEntityMovedInCycleToCurrentCycle() {
        entityMovedInCycle = currentRenderCycle
    }

    fun extract(currentWriteState: RenderState) {
        batchingSystem.addRenderBatches(currentWriteState)
    }
}
