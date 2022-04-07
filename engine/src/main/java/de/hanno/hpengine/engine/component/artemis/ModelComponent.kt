package de.hanno.hpengine.engine.component.artemis

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.sizeInBytes
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.utils.IntBag
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.model.*
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.ProgramDescription
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.engine.scene.BatchKey
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.scene.VertexStruktPacked
import de.hanno.hpengine.engine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.system.Extractor
import de.hanno.struct.copyTo
import org.joml.FrustumIntersection
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import struktgen.typed
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}

// TODO: Extract model cache for entities with only modelcomponent
@All(
    ModelComponent::class,
    TransformComponent::class,
)
class ModelSystem(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val textureManager: TextureManager,
    val materialManager: MaterialManager,
    val programManager: ProgramManager<*>,
    val entityBuffer: EntityBuffer,
) : BaseEntitySystem(), Extractor {
    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>

    val vertexIndexBufferStatic = VertexIndexBuffer(gpuContext, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(gpuContext, 10)

    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()

    val allocations: MutableMap<ModelComponentDescription, Allocation> = mutableMapOf()

    private var gpuJointsArray = BufferUtils.createByteBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)

    var gpuEntitiesArray = entityBuffer.underlying
    val entityIndices: MutableMap<ModelComponentDescription, Int> = mutableMapOf()
    private val modelCache = mutableMapOf<ModelComponentDescription, Model<*>>()
    private val programCache = mutableMapOf<ProgramDescription, Program<FirstPassUniforms>>()
    private val staticModelLoader = StaticModelLoader()

    operator fun get(modelComponentDescription: ModelComponentDescription) = modelCache[modelComponentDescription]

    override fun inserted(entityId: Int) {
        loadModelToCache(entityId)

        cacheEntityIndices()
        allocateVertexIndexBufferSpace() // TODO: Move this out of here so that it can be batch allocated below

    }

    override fun removed(entityId: Int) {
        cacheEntityIndices()
    }

    override fun inserted(entities: IntBag) {
        entities.forEach { entityId ->
            loadModelToCache(entityId)
        }

        cacheEntityIndices()
        allocateVertexIndexBufferSpace()
    }

    private fun loadModelToCache(entityId: Int) {
        val modelComponent = modelComponentMapper[entityId]
        val descr = modelComponent.modelComponentDescription
        val model = when (descr) {
            is AnimatedModelComponentDescription -> TODO()
            is StaticModelComponentDescription -> {

                val dir = when (descr.directory) {
                    Directory.Game -> config.gameDir
                    Directory.Engine -> config.engineDir
                }

                staticModelLoader.load(
                    descr.file,
                    textureManager,
                    dir
                )
            }
        }

        modelComponent.modelComponentDescription.material =
            modelComponent.modelComponentDescription.material ?: model.material
        modelCache[descr] = model

        model.meshes.forEach { mesh ->
            getProgramDescriptionOrNull(mesh, model, modelComponent)?.let { programDescription ->
                programCache[programDescription] =
                    programManager.getFirstPassProgram(programDescription) as Program<FirstPassUniforms>
            }
        }

        boundingVolumeComponentMapper.create(entityId).boundingVolume = model.boundingVolume
    }

    override fun processSystem() {
        updateGpuEntitiesArray()
        updateGpuJointsArray()

        var counter = 0
        val materials = materialManager.materials

        val actives = subscription.entities
        val ids = actives.data
        var i = 0
        val s = actives.size()
        while (s > i) {
            val entityId = ids[i]
            val modelComponent = modelComponentMapper[entityId]
            val model = modelCache[modelComponent.modelComponentDescription]!!
            val transformComponent = transformComponentMapper[entityId]
            val transform = transformComponent.transform

            val allocation = allocations[modelComponent.modelComponentDescription]!!

            val meshes = modelCache[modelComponent.modelComponentDescription]!!.meshes

            var currentEntity = gpuEntitiesArray[counter]
            val entityBufferIndex = entityId

            gpuEntitiesArray.byteBuffer.run {
                for ((targetMeshIndex, mesh) in meshes.withIndex()) {
                    val targetMaterialIndex = materials.indexOf(mesh.material)
                    currentEntity.run {
                        materialIndex = 0//targetMaterialIndex TODO: Reimplement
                        update = Update.STATIC.asDouble.toInt()//entity.updateType.asDouble.toInt() TODO: Reimplement
                        meshBufferIndex = entityBufferIndex + targetMeshIndex
                        meshIndex = targetMeshIndex
                        baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
                        baseJointIndex = allocation.baseJointIndex
                        animationFrame0 = 0//modelComponent.animationFrame0 TODO: Reimplement
                        isInvertedTexCoordY = 0//if (modelComponent.isInvertTexCoordY) 1 else 0 TODO: Reimplement
                        val boundingVolume = model.getBoundingVolume(transform, mesh)
                        dummy4 = allocation.indexOffset
                        setTrafoAndBoundingVolume(transform.transformation, boundingVolume)
                    }
                    counter++
                    currentEntity = gpuEntitiesArray[counter]

//                    // TODO: Reimplement clusters
//                    for (cluster in entity.clusters) {
//                        // TODO: This is so lame, but for some reason extraction has to be done twice. investigate here!
////                    if(cluster.updatedInCycle == -1L || cluster.updatedInCycle == 0L || cluster.updatedInCycle >= scene.currentCycle) {
//                        for (instance in cluster) {
//                            currentEntity = gpuEntitiesArray[counter]
//                            val instanceMatrix = instance.transform.transformation
//                            val instanceMaterialIndex =
//                                if (instance.materials.isEmpty()) targetMaterialIndex else materials.indexOf(instance.materials[targetMeshIndex])
//
//                            currentEntity.run {
//                                materialIndex = instanceMaterialIndex
//                                update = entity.updateType.ordinal
//                                meshBufferIndex = entityBufferIndex + targetMeshIndex
//                                entityIndex = entity.index
//                                meshIndex = targetMeshIndex
//                                baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
//                                baseJointIndex = allocation.baseJointIndex
//                                animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
//                                isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
//                                dummy4 = allocation.indexOffset
//                                setTrafoAndBoundingVolume(instanceMatrix, instance.spatial.boundingVolume)
//                            }
//
//                            counter++
//                        }
//                        cluster.updatedInCycle++
//                    }

//                    // TODO: Reimplement parenting
//                    if (entity.hasParent) {
//                        for (instance in entity.instances) {
//                            val instanceMatrix = instance.transform.transformation
//
//                            currentEntity.run {
//                                materialIndex = targetMaterialIndex
//                                update = entity.updateType.ordinal
//                                meshBufferIndex = entityBufferIndex + targetMeshIndex
//                                entityIndex = entity.index
//                                meshIndex = targetMeshIndex
//                                baseVertex = allocation.forMeshes.first().vertexOffset
//                                baseJointIndex = allocation.baseJointIndex
//                                animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
//                                isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
//                                val boundingVolume = instance.spatial.boundingVolume
//                                setTrafoAndBoundingVolume(instanceMatrix, boundingVolume)
//                            }
//
//                            counter++
//                            currentEntity = gpuEntitiesArray[counter]
//                        }
                }
            }
            i++
        }
    }

    private fun updateGpuEntitiesArray() {
        gpuEntitiesArray = gpuEntitiesArray.enlarge(requiredEntityBufferSize)
    }

    private val requiredEntityBufferSize: Int
        get() {
            // TODO: Reimplement instancing
            return subscription.entities.size() //* instances.count
        }

    private fun updateGpuJointsArray() {
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)

        for ((index, joint) in joints.withIndex()) {
            gpuJointsArray[index].run {
                set(gpuJointsArray.byteBuffer, joint)
            }
        }
    }

    fun cacheEntityIndices() {
        entityIndices.clear()

        val actives = subscription.entities
        val ids = actives.data
        val s = actives.size()
        var i = 0
        var index = 0
        while (s > i) {
            val current = modelComponentMapper[ids[i]]
            entityIndices.put(current.modelComponentDescription, index)
//            TODO: Reimplement instancing
            index += modelCache[current.modelComponentDescription]!!.meshes.size //* instances.count
            i++
        }
    }

    fun allocateVertexIndexBufferSpace() {
        val allocations = modelComponentMapper.hackedOutComponents.associateWith { c ->
            modelCache[c.modelComponentDescription]?.let { model ->
                if (model.isStatic) {
                    val vertexIndexBuffer = vertexIndexBufferStatic
                    val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                    val vertexIndexOffsetsForMeshes = c.putToBuffer(
                        vertexIndexBuffer,
                        vertexIndexOffsets
                    )
                    Allocation.Static(vertexIndexOffsetsForMeshes)
                } else {
                    val vertexIndexBuffer = vertexIndexBufferAnimated
                    val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                    val vertexIndexOffsetsForMeshes = c.putToBuffer(
                        vertexIndexBuffer,
                        vertexIndexOffsets
                    )

                    val elements = (model as AnimatedModel).animation.frames
                        .flatMap { frame -> frame.jointMatrices.toList() }
                    val jointsOffset = joints.size
                    joints.addAll(elements)
                    Allocation.Animated(vertexIndexOffsetsForMeshes, jointsOffset)
                }
            }
        }.mapKeys { it.key.modelComponentDescription }.filter { it.value != null } as Map<ModelComponentDescription, Allocation>

        this.allocations.putAll(allocations)
    }

    fun VertexIndexBuffer.allocateForComponent(modelComponent: ModelComponent): VertexIndexBuffer.VertexIndexOffsets {
        val model = modelCache[modelComponent.modelComponentDescription]!!
        return allocate(model.uniqueVertices.size, model.indices.size)
    }

    fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexBuffer.VertexIndexOffsets): List<VertexIndexBuffer.VertexIndexOffsets> {
        var currentIndexOffset = vertexIndexOffsets.indexOffset
        var currentVertexOffset = vertexIndexOffsets.vertexOffset

        val model = modelCache[modelComponentDescription]!!

        return model.meshes.indices.map { i ->
            val mesh = model.meshes[i] as Mesh<*>
            VertexIndexBuffer.VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
                currentIndexOffset += mesh.indexBufferValues.size
                currentVertexOffset += mesh.vertices.size
            }
        }
    }

    fun ModelComponent.putToBuffer(
        indexBuffer: VertexIndexBuffer,
        vertexIndexOffsets: VertexIndexBuffer.VertexIndexOffsets
    ): List<VertexIndexBuffer.VertexIndexOffsets> {

        synchronized(indexBuffer) {
            val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)
            val model = modelCache[modelComponentDescription]!!
            when (model) {
                is StaticModel -> indexBuffer.vertexStructArray.addAll(
                    vertexIndexOffsets.vertexOffset * VertexStruktPacked.sizeInBytes,
                    model.verticesPacked.byteBuffer
                )
                is AnimatedModel -> indexBuffer.animatedVertexStructArray.addAll(
                    vertexIndexOffsets.vertexOffset * AnimatedVertexStruktPacked.sizeInBytes,
                    model.verticesPacked.byteBuffer
                )
            } // TODO: sealed classes!!

            indexBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, model.indices)

            return vertexIndexOffsetsForMeshes
        }
    }

    override fun extract(currentWriteState: RenderState) {
        cacheEntityIndices() // TODO: Move this to update step
        currentWriteState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        currentWriteState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated

        currentWriteState.entitiesState.jointsBuffer.ensureCapacityInBytes(gpuJointsArray.byteBuffer.capacity())
        currentWriteState.entitiesState.entitiesBuffer.ensureCapacityInBytes(gpuEntitiesArray.byteBuffer.capacity())
        gpuJointsArray.byteBuffer.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer, true)
        gpuEntitiesArray.byteBuffer.copyTo(currentWriteState.entitiesBuffer.buffer, true)

        extract(
            currentWriteState.camera, currentWriteState, currentWriteState.camera.getPosition(),
            config.debug.isDrawLines, allocations, entityIndices,
        )
    }

    fun extract(
        camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, drawLines: Boolean,
        allocations: MutableMap<ModelComponentDescription, Allocation>,
        entityIndices: MutableMap<ModelComponentDescription, Int>
    ) {

        currentWriteState.entitiesState.renderBatchesStatic.clear()
        currentWriteState.entitiesState.renderBatchesAnimated.clear()

        var index = 0
        forEachEntity { entityId ->
            val modelComponent = modelComponentMapper[entityId]
            val transform = transformComponentMapper[entityId].transform
            val entityVisible = !invisibleComponentMapper.has(entityId)

            val entityIndexOf = entityIndices[modelComponent.modelComponentDescription]!!

            val model: Model<*> = modelCache[modelComponent.modelComponentDescription]!!
            val meshes = model.meshes
            for (meshIndex in meshes.indices) {
                val mesh = meshes[meshIndex]
                val meshCenter = mesh.spatial.getCenter(transform.transformation)
                val boundingSphereRadius = model.getBoundingSphereRadius(mesh)

                val (min1, max1) = model.getBoundingVolume(transform, mesh)
                val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                val meshIsInFrustum =
                    intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE


                val visibleForCamera = meshIsInFrustum// || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + meshIndex //* entity.instanceCount

                val batch =
                    (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh,-1)) { (_, _) -> RenderBatch() }
                with(batch) {
                    entityBufferIndex = meshBufferIndex
                    this.movedInCycle = currentWriteState.cycle// entity.movedInCycle TODO: reimplement
                    this.isDrawLines = drawLines
                    this.cameraWorldPosition = cameraWorldPosition
                    this.isVisible = entityVisible
                    this.isVisibleForCamera = visibleForCamera
                    update = Update.STATIC//entity.updateType TODO: reimplement
                    entityMinWorld.set(model.getBoundingVolume(mesh).min)//TODO: reimplement with transform
                    entityMaxWorld.set(model.getBoundingVolume(mesh).max)//TODO: reimplement with transform
                    meshMinWorld.set(min1)
                    meshMaxWorld.set(max1)
                    centerWorld = meshCenter
                    this.boundingSphereRadius = boundingSphereRadius
                    with(drawElementsIndirectCommand) {
                        this.primCount = 1
                        this.count = model.meshIndexCounts[meshIndex]
                        this.firstIndex =
                            allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].indexOffset
                        this.baseVertex =
                            allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].vertexOffset
                    }
                    this.animated = !model.isStatic
                    materialInfo = mesh.material
                    program = getProgramDescriptionOrNull(mesh, model, modelComponent)?.let { programCache[it]!! }
                    entityIndex = index //TODO: check if correct index, it got out of hand
                    entityName = mesh.name // TODO: use entity name component
                    contributesToGi = true//entity.contributesToGi TODO: reimplement
                    this.meshIndex = meshIndex
                }

                if (batch.isStatic) {
                    currentWriteState.addStatic(batch)
                } else {
                    currentWriteState.addAnimated(batch)
                }
            }
            index++
        }
    }

    private fun getProgramDescriptionOrNull(
        mesh: Mesh<out Any?>,
        model: Model<*>,
        modelComponent: ModelComponent
    ) = (
            mesh.material.programDescription ?: (model.material.programDescription
                ?: modelComponent.modelComponentDescription.material?.let { it }?.programDescription)
            )
}


sealed class Allocation(val forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>) {
    init {
        require(forMeshes.isNotEmpty())
    }

    val indexOffset = forMeshes.first().indexOffset
    val vertexOffset = forMeshes.first().vertexOffset

    class Static(forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>) : Allocation(forMeshes)
    class Animated(forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>, val jointsOffset: Int) :
        Allocation(forMeshes)
}

val Allocation.baseJointIndex: Int
    get() = when (this) {
        is Allocation.Static -> 0
        is Allocation.Animated -> jointsOffset
    }

fun <T> BaseEntitySystem.mapEntity(block: (Int) -> T): MutableList<T> {
    val result = mutableListOf<T>()
    val actives: IntBag = subscription.entities
    val ids = actives.data
    var i = 0
    val s = actives.size()
    while (s > i) {
        result.add(block(ids[i]))
        i++
    }
    return result
}

inline fun <T> BaseEntitySystem.forEachEntity(block: (Int) -> T) {
    subscription.entities.forEach(block)
}

inline fun <T> IntBag.forEach(block: (Int) -> T) {
    val ids = data
    var i = 0
    val s = size()
    while (s > i) {
        block(ids[i])
        i++
    }
}