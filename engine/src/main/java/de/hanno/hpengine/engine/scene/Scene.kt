package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.graphics.light.AreaLight
import de.hanno.hpengine.engine.graphics.light.PointLight
import de.hanno.hpengine.engine.graphics.light.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.graphics.light.LightManager
import de.hanno.hpengine.engine.instacing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.SystemsRegistry
import de.hanno.hpengine.engine.manager.SimpleSystemsRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.util.script.ScriptManager
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import org.joml.Vector4f
import org.nustaq.serialization.FSTConfiguration
import java.io.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.logging.Logger

class Scene @JvmOverloads constructor(name: String = "new-scene-" + System.currentTimeMillis(), val engine: Engine, sceneManager: SceneManager = engine.sceneManager) : LifeCycle, Serializable {
    var name = ""
    init {
        this.name = name
    }

    val systems: SystemsRegistry = SimpleSystemsRegistry()
    val entityManager = EntityManager(engine, engine.eventBus)
    val environmentProbeManager = EnvironmentProbeManager(engine)
    val clusterComponentSystem = systems.register(ClustersComponentSystem(engine))
    val cameraComponentSystem = systems.register(CameraComponentSystem(engine))
    val inputComponentSystem = systems.register(InputComponentSystem(engine))
    val modelComponentSystem = systems.register(ModelComponentSystem(engine))
    val lightManager = LightManager(engine.eventBus, engine.materialManager, sceneManager, engine.gpuContext, engine.programManager, inputComponentSystem, modelComponentSystem)
    val scriptManager = ScriptManager().apply { defineGlobals(engine, entityManager) }

    val camera = entityManager.create()
            .apply { addComponent(inputComponentSystem.create(this)) }
            .apply { addComponent(cameraComponentSystem.create(this)) }
            .apply { entityManager.add(this) }

    var activeCamera = camera

    private val probes = CopyOnWriteArrayList<ProbeData>()

    @Volatile
    @Transient private var entityMovedInCycle: Long = 0
    @Volatile
    @Transient
    var entityAddedInCycle: Long = 0
        private set
    @Volatile
    @Transient private var directionalLightMovedInCycle: Long = 0
    @Volatile
    @Transient private var pointLightMovedInCycle: Long = 0
    @Volatile
    @Transient
    var currentCycle: Long = 0
    @Volatile
    @Transient
    var isInitiallyDrawn: Boolean = false
    private val min = Vector4f()
    private val max = Vector4f()
    val minMax = arrayOf(min, max)

    private val tempDistVector = Vector3f()

    override fun init(engine: Engine) {
        super.init(engine)
        engine.eventBus.register(this)
        engine.eventBus.post(SceneInitEvent())
    }

    fun write() {
        write(name)
    }

    fun write(name: String): Boolean {
        val fileName = FilenameUtils.getBaseName(name)
        this.name = fileName
        var fos: FileOutputStream? = null
        var out: ObjectOutputStream? = null
        try {
            fos = FileOutputStream(directory + fileName + ".hpscene")
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
        calculateMinMax(entityManager.entities)
    }

    private fun calculateMinMax(entities: List<Entity>) {
        if (entities.size == 0) {
            min.set(-1f, -1f, -1f, -1f)
            max.set(1f, 1f, 1f, 1f)
            return
        }

        min.set(absoluteMaximum)
        max.set(absoluteMinimum)

        for (entity in entities) {
            val (currentMin, currentMax) = entity.minMaxWorld
            min.x = if (currentMin.x < min.x) currentMin.x else min.x
            min.y = if (currentMin.y < min.y) currentMin.y else min.y
            min.z = if (currentMin.z < min.z) currentMin.z else min.z

            max.x = if (currentMax.x > max.x) currentMax.x else max.x
            max.y = if (currentMax.y > max.y) currentMax.y else max.y
            max.z = if (currentMax.z > max.z) currentMax.z else max.z
        }
    }

    fun addAll(entities: List<Entity>) {
        entityManager.add(entities)
        modelComponentSystem.allocateVertexIndexBufferSpace(entities)
        calculateMinMax(entities)
        entityAddedInCycle = currentCycle
        engine.eventBus.post(MaterialAddedEvent())
        engine.eventBus.post(EntityAddedEvent())
    }

    fun add(entity: Entity) = addAll(listOf(entity))

    override fun update(engine: Engine, seconds: Float) {
        systems.update(seconds)
        val entities = entityManager.entities

        for (i in entities.indices) {
            try {
                entities[i].update(engine, seconds)
            } catch (e: Exception) {
                LOGGER.warning(e.message)
            }

        }

        for (i in 0 until getPointLights().size) {
            val pointLight = getPointLights()[i]
            if (!pointLight.hasMoved()) {
                continue
            }
            pointLightMovedInCycle = currentCycle
            engine.eventBus.post(PointLightMovedEvent())
            pointLight.isHasMoved = false
        }

        for (i in 0 until entityManager.entities.size) {
            val entity = entityManager.entities[i]
            if (!entity.hasMoved()) {
                continue
            }
            calculateMinMax()
            entity.isHasMoved = false
            entityMovedInCycle = currentCycle
        }
    }

    fun getEntities(): List<Entity> {
        return entityManager.entities
    }

    fun getEntity(name: String): Optional<Entity> {
        val candidates = entityManager.entities.filter { e -> e.name == name }
        return if (candidates.isNotEmpty()) Optional.of(candidates[0]) else Optional.ofNullable(null)
    }

    fun getPointLights(): List<PointLight> {
        return engine.getScene().lightManager.pointLights
    }

    fun getTubeLights(): List<TubeLight> {
        return engine.getScene().lightManager.tubeLights
    }
    fun getAreaLights(): List<AreaLight> = engine.getScene().lightManager.areaLights

    fun addPointLight(pointLight: PointLight) {
        engine.getScene().lightManager.pointLights.add(pointLight)
        engine.eventBus.post(LightChangedEvent())
    }

    fun addTubeLight(tubeLight: TubeLight) {
        engine.getScene().lightManager.tubeLights.add(tubeLight)
    }

    fun addRenderBatches(engine: Engine, camera: Camera, currentWriteState: RenderState) {
        val cameraWorldPosition = camera.entity.position

        val firstpassDefaultProgram = engine.programManager.firstpassDefaultProgram

        addBatches(camera, currentWriteState, cameraWorldPosition, firstpassDefaultProgram, modelComponentSystem.getComponents())

    }

    fun addBatches(camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, program: Program, modelComponents: List<ModelComponent>) {
        addBatches(camera, currentWriteState, cameraWorldPosition, program, modelComponents, Consumer{ batch ->
            if (batch.isStatic) {
                currentWriteState.addStatic(batch)
            } else {
                currentWriteState.addAnimated(batch)
            }
        })
    }

    fun addBatches(camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, firstpassDefaultProgram: Program, modelComponents: List<ModelComponent>, addToRenderStateRunnable: Consumer<RenderBatch>) {
        for (modelComponent in modelComponents) {
            val entity = modelComponent.entity
            val distanceToCamera = tempDistVector.length()
            val isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.boundingSphereRadius

            val entityIndexOf = entity.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY).entityBufferIndex

            val meshes = modelComponent.meshes
            for (i in meshes.indices) {
                val mesh = meshes[i]
                val meshCenter = mesh.getCenter(entity)
                val boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh)
                val meshIsInFrustum = camera.frustum.sphereInFrustum(meshCenter.x, meshCenter.y, meshCenter.z, boundingSphereRadius)//TODO: Fix this
                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances

                mesh.material.setTexturesUsed()
                val (min1, max1) = modelComponent.getMinMax(entity, mesh)
                val meshBufferIndex = entityIndexOf + i * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (mesh1, clusterIndex) -> RenderBatch() }
                batch.init(firstpassDefaultProgram, meshBufferIndex, entity.isVisible, entity.isSelected, Config.getInstance().isDrawLines, cameraWorldPosition, isInReachForTextureLoading, entity.instanceCount, visibleForCamera, entity.update, min1, max1, meshCenter, boundingSphereRadius, modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), !modelComponent.model.isStatic, entity.instanceMinMaxWorlds)
                addToRenderStateRunnable.accept(batch)
            }
        }
    }

    fun entityMovedInCycle(): Long {
        return entityMovedInCycle
    }

    fun pointLightMovedInCycle(): Long {
        return pointLightMovedInCycle
    }

    companion object {
        private const val serialVersionUID = 1L

        private val LOGGER = Logger.getLogger(Scene::class.java.name)

        private val absoluteMaximum = Vector4f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
        private val absoluteMinimum = Vector4f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

        internal var conf = FSTConfiguration.createDefaultConfiguration()

        private fun handleEvolution(scene: Scene) {}

        @JvmStatic val directory: String
            get() = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
