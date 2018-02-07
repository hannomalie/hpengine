package de.hanno.hpengine.engine.scene

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.Engine.Companion.eventBus
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.container.EntitiesContainer
import de.hanno.hpengine.engine.container.SimpleContainer
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.graphics.light.AreaLight
import de.hanno.hpengine.engine.graphics.light.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.PointLight
import de.hanno.hpengine.engine.graphics.light.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.model.Entity
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import org.joml.Vector4f
import org.nustaq.serialization.FSTConfiguration
import java.io.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.logging.Logger


class Scene @JvmOverloads constructor(name: String = "new-scene-" + System.currentTimeMillis()) : LifeCycle, Serializable {

    var name = ""
    private val probes = CopyOnWriteArrayList<ProbeData>()

    @Transient
    var entitiesContainer: EntitiesContainer = SimpleContainer()
        private set
    @Transient private var initialized = false
    private val entities = CopyOnWriteArrayList<Entity>()
    val joints: List<BufferableMatrix4f> = CopyOnWriteArrayList()
    private val pointLights = CopyOnWriteArrayList<PointLight>()
    private val tubeLights = CopyOnWriteArrayList<TubeLight>()
    val areaLights: List<AreaLight> = CopyOnWriteArrayList()
    val directionalLight = DirectionalLight()

    @Volatile
    @Transient private var updateCache = true
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
    @Transient private var currentCycle: Long = 0
    @Volatile
    @Transient
    var isInitiallyDrawn: Boolean = false
    private var engine: Engine? = null
    private val min = Vector4f()
    private val max = Vector4f()
    val minMax = arrayOf(min, max)

    internal var registeredModelComponents: MutableList<ModelComponent> = CopyOnWriteArrayList()


    val modelComponents: List<ModelComponent>
        get() = registeredModelComponents

    private val entityIndices = IntArrayList()

    private val tempDistVector = Vector3f()

    init {
        this.name = name
    }

    override fun init(engine: Engine) {
        super.init(engine)
        this.engine = engine
        eventBus.register(this)
        entitiesContainer = SimpleContainer()
        entities.forEach(Consumer { it.initialize() })
        entities.forEach { entity -> entity.getComponents().values.forEach { c -> c.registerInScene(this@Scene, engine) } }
        addAll(entities)
        for (data in probes) {
            engine.gpuContext.execute {
                try {
                    // TODO: Remove this f***
                    val probe = engine.environmentProbeManager.getProbe(data.center, data.size, data.update, data.weight)
                    engine.renderer.addRenderProbeCommand(probe)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        initLights()
        initialized = true
        eventBus.post(SceneInitEvent())
    }

    private fun initLights() {
        for (pointLight in pointLights) {
            pointLight.initialize()
        }
        for (areaLight in areaLights) {
            areaLight.initialize()
        }

        directionalLight.addInputController()
        directionalLight.initialize()
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
            entities.clear()
            entities.addAll(entitiesContainer.entities)
            probes.clear()
            //			for (EnvironmentProbe probe : Engine.getInstance().getEnvironmentProbeManager().getProbes()) {
            //				ProbeData probeData = new ProbeData(probe.getCenter(), probe.getSize(), probe.getProbeUpdate());
            //				if(probes.contains(probeData)) { continue; }
            //				probes.add(probeData);
            //			}
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
        calculateMinMax(entitiesContainer.entities)
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
        entitiesContainer.insert(entities)
        entities.forEach { e ->
            e.getComponents().values.forEach { c ->
                c.registerInScene(this@Scene, engine)
                this@Scene.register(c)
            }
        }
        calculateMinMax(entities)
        updateCache = true
        entityAddedInCycle = currentCycle
        eventBus.post(MaterialAddedEvent())
        eventBus.post(EntityAddedEvent())
    }

    fun add(entity: Entity) = addAll(listOf(entity))

    //TODO: Handle deregistration, or prohibit it
    private fun register(c: Component) {
        if (c is ModelComponent) {
            registeredModelComponents.add(c)
        }
    }

    fun getEntityBufferIndex(modelComponent: ModelComponent): Int {
        cacheEntityIndices()
        val index = modelComponents.indexOf(modelComponent)
        return if (index < 0 || index > entityIndices.size()) {
            -1
        } else entityIndices.get(index)
    }

    private fun cacheEntityIndices() {
        if (updateCache) {
            updateCache = false
            entityIndices.clear()
            var index = 0
            for (current in entitiesContainer.entities) {
                if (!current.hasComponent(ModelComponent::class.java)) {
                    continue
                }
                entityIndices.add(index)
                index += current.instanceCount * current.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)!!.meshes.size
            }
        }
    }

    override fun update(engine: Engine, seconds: Float) {
        cacheEntityIndices()
        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            pointLightsIterator.next().update(engine, seconds)
        }

        for (i in areaLights.indices) {
            areaLights[i].update(engine, seconds)
        }
        val entities = entitiesContainer.entities

        for (i in entities.indices) {
            try {
                entities[i].update(engine, seconds)
            } catch (e: Exception) {
                LOGGER.warning(e.message)
            }

        }
        directionalLight.update(engine, seconds)

        for (i in 0 until getPointLights().size) {
            val pointLight = getPointLights()[i]
            if (!pointLight.hasMoved()) {
                continue
            }
            pointLightMovedInCycle = currentCycle
            eventBus.post(PointLightMovedEvent())
            pointLight.isHasMoved = false
        }

        for (i in 0 until entitiesContainer.entities.size) {
            val entity = entitiesContainer.entities[i]
            if (!entity.hasMoved()) {
                continue
            }
            calculateMinMax()
            entity.isHasMoved = false
            entityMovedInCycle = currentCycle
        }

        if (directionalLight.hasMoved()) {
            directionalLightMovedInCycle = currentCycle
            directionalLight.isHasMoved = false
        }
    }

    fun getEntities(): List<Entity> {
        return entitiesContainer.entities
    }

    fun getEntity(name: String): Optional<Entity> {
        val candidates = entitiesContainer.entities.filter { e -> e.name == name }
        return if (candidates.isNotEmpty()) Optional.of(candidates[0]) else Optional.ofNullable(null)
    }

    override fun isInitialized(): Boolean {
        return initialized
    }

    fun setInitialized(initialized: Boolean) {
        this.initialized = initialized
    }

    fun removeEntity(entity: Entity): Boolean {
        return entitiesContainer.remove(entity)
    }

    fun getPointLights(): List<PointLight> {
        return pointLights
    }

    fun getTubeLights(): List<TubeLight> {
        return tubeLights
    }

    fun addPointLight(pointLight: PointLight) {
        pointLights.add(pointLight)
        eventBus.post(LightChangedEvent())
    }

    fun addTubeLight(tubeLight: TubeLight) {
        tubeLights.add(tubeLight)
    }

    fun addRenderBatches(engine: Engine, camera: Camera, currentWriteState: RenderState) {
        val cameraWorldPosition = camera.position

        val firstpassDefaultProgram = engine.programManager.firstpassDefaultProgram

        val modelComponentsStatic = engine.sceneManager.scene.modelComponents

        addBatches(engine, camera, currentWriteState, cameraWorldPosition, firstpassDefaultProgram, modelComponentsStatic)

    }

    fun addBatches(engine: Engine, camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, program: Program, modelComponentsStatic: List<ModelComponent>) {
        addBatches(engine, camera, currentWriteState, cameraWorldPosition, program, modelComponentsStatic, Consumer{ batch ->
            if (batch.isStatic()) {
                currentWriteState.addStatic(batch)
            } else {
                currentWriteState.addAnimated(batch)
            }
        })
    }

    fun addBatches(engine: Engine, camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, firstpassDefaultProgram: Program, modelComponents: List<ModelComponent>, addToRenderStateRunnable: Consumer<RenderBatch>) {
        for (modelComponent in modelComponents) {
            val entity = modelComponent.entity
            val distanceToCamera = tempDistVector.length()
            val isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.boundingSphereRadius

            val entityIndexOf = engine.sceneManager.scene.getEntityBufferIndex(entity.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY))

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

                val batch = (currentWriteState.entitiesState.cash as java.util.Map<BatchKey, RenderBatch>).computeIfAbsent(BatchKey(mesh, -1)) { (mesh1, clusterIndex) -> RenderBatch() }
                batch.init(firstpassDefaultProgram, meshBufferIndex, entity.isVisible, entity.isSelected, Config.getInstance().isDrawLines, cameraWorldPosition, isInReachForTextureLoading, entity.instanceCount, visibleForCamera, entity.update, min1, max1, meshCenter, boundingSphereRadius, modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), !modelComponent.model.isStatic, entity.instanceMinMaxWorlds)
                addToRenderStateRunnable.accept(batch)
            }
        }
    }

    fun setUpdateCache(updateCache: Boolean) {
        this.updateCache = updateCache
    }

    fun entityMovedInCycle(): Long {
        return entityMovedInCycle
    }

    fun setCurrentCycle(currentCycle: Long) {
        this.currentCycle = currentCycle
    }

    fun directionalLightMovedInCycle(): Long {
        return directionalLightMovedInCycle
    }

    fun pointLightMovedInCycle(): Long {
        return pointLightMovedInCycle
    }

    companion object {
        private const val serialVersionUID = 1L

        private val LOGGER = Logger.getLogger(Scene::class.java.name)

        @JvmStatic fun read(name: String): Scene {
            val fileName = FilenameUtils.getBaseName(name)
            var fis: FileInputStream? = null
            var `in`: ObjectInputStream? = null
            return try {
                fis = FileInputStream(directory + fileName + ".hpscene")
                `in` = ObjectInputStream(fis)
                val scene = `in`.readObject() as Scene

                //			FSTObjectInput newIn = new FSTObjectInput(in);
                //			Scene de.hanno.hpengine.scene = (Scene)newIn.readObject();
                //			newIn.close();

                handleEvolution(scene)
                `in`.close()
                fis.close()
                scene.entitiesContainer = SimpleContainer()//new Octree(new Vector3f(), 400, 6);
                scene
            } catch (e: IOException) {
                e.printStackTrace()
                throw e
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                throw e
            }
        }

        private val absoluteMaximum = Vector4f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
        private val absoluteMinimum = Vector4f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

        internal var conf = FSTConfiguration.createDefaultConfiguration()

        private fun handleEvolution(scene: Scene) {}

        @JvmStatic val directory: String
            get() = DirectoryManager.WORKDIR_NAME + "/assets/scenes/"
    }
}
