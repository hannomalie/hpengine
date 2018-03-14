package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
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
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateConsumer
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

class Scene @JvmOverloads constructor(val name: String = "new-scene-" + System.currentTimeMillis(), val engine: Engine) : LifeCycle, Serializable {

    @Transient private var entityMovedInCycle: Long = 0
    @Transient var entityAddedInCycle: Long = 0
    @Transient var currentCycle: Long = 0
    @Transient var isInitiallyDrawn: Boolean = false
    val minMax = AABB(Vector3f(), 100f)

    val renderStateConsumers = mutableListOf<StateConsumer>()
    val componentSystems: SystemsRegistry = SimpleSystemsRegistry()
    val managers: ManagerRegistry = SimpleManagerRegistry()

    val entityManager = (EntityManager(engine, engine.eventBus))
    val environmentProbeManager = managers.register(engine.environmentProbeManager)

    val clusterComponentSystem = componentSystems.register(ClustersComponentSystem(engine))
    val cameraComponentSystem = componentSystems.register(CameraComponentSystem(engine))
    val inputComponentSystem = componentSystems.register(InputComponentSystem(engine))
    val modelComponentSystem = componentSystems.register(ModelComponentSystem(engine))
    val pointLightComponentSystem = componentSystems.register(PointLightComponentSystem())
    val areaLightComponentSystem = componentSystems.register(AreaLightComponentSystem())
    val tubeLightComponentSystem = componentSystems.register(TubeLightComponentSystem())

    val materialManager = managers.register(MaterialManager(engine, engine.textureManager))
    val scriptManager = managers.register(ScriptManager().apply { defineGlobals(engine, entityManager, materialManager) })

    val entitySystems = SimpleEntitySystemRegistry()
    val directionalLightSystem = entitySystems.register(DirectionalLightSystem(engine, this, engine.eventBus))
    val batchingSystem = entitySystems.register(BatchingSystem(engine, this))
    val pointLightSystem = entitySystems.register(PointLightSystem(engine, this)).apply { renderStateConsumers.add(this) }
    val areaLightSystem = entitySystems.register(AreaLightSystem(engine, this)).apply { renderStateConsumers.add(this) }

    val directionalLight = Entity()
            .apply { addComponent(DirectionalLight(this)) }
            .apply { addComponent(DirectionalLight.DirectionalLightController(engine, this)) }
            .apply { this@Scene.add(this) }

    val camera = entityManager.create()
            .apply { addComponent(inputComponentSystem.create(this)) }
            .apply { addComponent(cameraComponentSystem.create(this)) }
            .apply { this@Scene.add(this) }

    var activeCamera = camera

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

    fun addAll(entities: List<Entity>) {
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

    fun add(entity: Entity) = addAll(listOf(entity))

    override fun update(deltaSeconds: Float) {
        managers.update(deltaSeconds)

        componentSystems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        entityManager.update(deltaSeconds)
        entityManager.afterUpdate()
    }

    fun getEntities(): List<Entity> {
        return entityManager.getEntities()
    }

    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }

    fun getPointLights(): List<PointLight> = pointLightComponentSystem.getComponents()
    fun getTubeLights(): List<TubeLight> = tubeLightComponentSystem.getComponents()
    fun getAreaLights(): List<AreaLight> = areaLightComponentSystem.getComponents()

    fun entityMovedInCycle() = entityMovedInCycle
    fun pointLightMovedInCycle() = pointLightSystem.pointLightMovedInCycle

    fun setEntityMovedInCycleToCurrentCycle() {
        entityMovedInCycle = currentCycle
    }

    fun extract(currentWriteState: RenderState) {
        batchingSystem.addRenderBatches(currentWriteState)
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmStatic val directory = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
