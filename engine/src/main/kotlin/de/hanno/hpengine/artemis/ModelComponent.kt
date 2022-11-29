package de.hanno.hpengine.artemis

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.sizeInBytes
import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.hackedOutComponents
import com.artemis.link.LinkListener
import com.artemis.utils.IntBag
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialManager
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.BatchKey
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.SimpleSpatial
import de.hanno.hpengine.transform.StaticTransformSpatial
import de.hanno.hpengine.transform.TransformSpatial
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.enlarge
import de.hanno.hpengine.graphics.vertexbuffer.appendIndices
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import struktgen.api.typed
import java.util.concurrent.CopyOnWriteArrayList


fun <T: Component> ComponentMapper<T>.getOrNull(entityId: Int): T? = if (has(entityId)) get(entityId) else null

class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}
class ModelCacheComponent : Component() {
    lateinit var model: Model<*>
    lateinit var allocation: Allocation
    lateinit var meshSpatials: List<SimpleSpatial>
}

class MaterialComponent: Component() {
    lateinit var material: Material
}
class InstancesComponent: Component() {
    val instances = mutableListOf<Int>()
}

context(GpuContext)
@One(
    ModelComponent::class,
//    InstanceComponent::class,
)
class ModelSystem(
    val config: Config,
    val textureManager: OpenGLTextureManager,
    val materialManager: MaterialManager,
    val programManager: ProgramManager,
    val entityBuffer: EntityBuffer,
) : BaseEntitySystem(), Extractor, LinkListener {
    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>
    lateinit var spatialComponentMapper: ComponentMapper<SpatialComponent>

    val vertexIndexBufferStatic = VertexIndexBuffer(10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(10)

    val joints: MutableList<Matrix4f> = CopyOnWriteArrayList()

    val allocations: MutableMap<ModelComponentDescription, Allocation> = mutableMapOf()

    private var gpuJointsArray = BufferUtils.createByteBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)

    var gpuEntitiesArray = entityBuffer.underlying
    val entityIndices: MutableMap<Int, Int> = mutableMapOf()
    fun getEntityIdForEntityBufferIndex(entityBufferIndex: Int): Int = gpuEntitiesArray.byteBuffer.run {
        return gpuEntitiesArray[entityBufferIndex].entityIndex
    }

    private val modelCache = mutableMapOf<ModelComponentDescription, Model<*>>()
    private val programCache = mutableMapOf<ProgramDescription, Program<FirstPassUniforms>>()
    private val staticModelLoader = StaticModelLoader()
    private val animatedModelLoader = AnimatedModelLoader()

    operator fun get(modelComponentDescription: ModelComponentDescription) = modelCache[modelComponentDescription]

    override fun inserted(entityId: Int) {
        loadModelToCache(entityId)

        cacheEntityIndices()
        allocateVertexIndexBufferSpace() // TODO: Move this out of here so that it can be batch allocated below

        modelCacheComponentMapper.getOrNull(entityId)?.apply {
            val instanceComponent = instanceComponentMapper.getOrNull(entityId)
            val modelComponentOrNull = modelComponentMapper.getOrNull(entityId)

            val modelComponent = modelComponentOrNull ?: modelComponentMapper[instanceComponent!!.targetEntity]
            val descr = modelComponent.modelComponentDescription
            this.allocation = allocations[descr]!!
        }

        requiredEntityBufferSize = calculateRequiredEntityBufferSize()
    }

    override fun removed(entities: IntBag?) {
        cacheEntityIndices()
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
        entities.forEach { entityId ->
            modelCacheComponentMapper.getOrNull(entityId)?.apply {
                val instanceComponent = instanceComponentMapper.getOrNull(entityId)
                val modelComponentOrNull = modelComponentMapper.getOrNull(entityId)

                val modelComponent = modelComponentOrNull ?: modelComponentMapper[instanceComponent!!.targetEntity]
                val descr = modelComponent.modelComponentDescription
                this.allocation = allocations[descr]!!
            }
        }
        requiredEntityBufferSize = calculateRequiredEntityBufferSize()
    }

    private fun loadModelToCache(entityId: Int) {
        val instanceComponent = instanceComponentMapper.getOrNull(entityId)
        val transformComponent = transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper[instanceComponent!!.targetEntity]
        val modelComponentOrNull = modelComponentMapper.getOrNull(entityId)
        val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)
        if(instanceComponent == null && modelComponentOrNull == null) return

        val modelComponent = modelComponentOrNull ?: modelComponentMapper[instanceComponent!!.targetEntity]
        val descr = modelComponent.modelComponentDescription
        val dir = when (descr.directory) {
            Directory.Game -> config.gameDir
            Directory.Engine -> config.engineDir
        }
        val model = modelCache.computeIfAbsent(descr) {
            when (descr) {
                is AnimatedModelComponentDescription -> {
                    animatedModelLoader.load(
                        descr.file,
                        textureManager,
                        dir
                    )
                }
                is StaticModelComponentDescription -> {
                    staticModelLoader.load(
                        descr.file,
                        textureManager,
                        dir
                    )
                }
            }.apply {
                meshes.forEach { mesh ->
                    val meshMaterial = materialComponentOrNull?.material ?: mesh.material
                    materialManager.registerMaterial(meshMaterial)
                    meshMaterial.programDescription?.let { programDescription ->
                        programCache[programDescription] =
                            programManager.getFirstPassProgram(programDescription, StaticFirstPassUniforms()) as Program<FirstPassUniforms>
                    }
                }
            }
        }

        boundingVolumeComponentMapper.create(entityId).boundingVolume = model.boundingVolume
        modelCacheComponentMapper.create(entityId).apply {
            this.model = model
            this.meshSpatials = model.meshes.map {
                val origin = it.spatial.boundingVolume
//                TODO: This doesn't work yet, aabbs are not calculated per mesh, figure out why
//                StaticTransformSpatial(
                TransformSpatial(
                    transformComponent.transform,
                    AABB(origin.localMin, origin.localMax),
                )
            }
        }
    }

    override fun processSystem() {
        updateGpuEntitiesArray()
        updateGpuJointsArray()

        var entityBufferIndex = 0

        forEachEntity { parentEntityId ->
            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances
            val entityIds = if(instances == null) arrayOf(parentEntityId) else arrayOf(parentEntityId) + instances

            for (entityId in entityIds) {
                val instanceComponent = instanceComponentMapper.getOrNull(entityId)
                val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)

                if(modelComponent != null) {
                    val modelCacheComponent = modelCacheComponentMapper.getOrNull(entityId) ?: modelCacheComponentMapper[parentEntityId]
                    val model = modelCacheComponent.model
                    when(model) {
                        is AnimatedModel -> model.animations.values.forEach {
                            it.update(world.delta)
                        }
                        is StaticModel -> {  }
                        else -> throw IllegalStateException("Hello compiler bug")
                    }
                    val transformComponent = transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(instanceComponent!!.targetEntity)
                    val transform = transformComponent!!.transform

                    val allocation = modelCacheComponent.allocation//allocations[modelComponent.modelComponentDescription]!!

                    val meshes = model.meshes


                    val animationFrame = when(model) {
                        is AnimatedModel -> model.animation.currentFrame
                        is StaticModel -> 0
                        else -> throw IllegalStateException() // Hello compiler bug
                    }
                    gpuEntitiesArray.byteBuffer.run {
                        for ((targetMeshIndex, mesh) in meshes.withIndex()) {
                            val currentEntity = gpuEntitiesArray[entityBufferIndex]

                            val meshMaterial = materialComponentOrNull?.material ?: mesh.material // TODO: Think about override per mesh instead of all at once
                            val targetMaterialIndex = meshMaterial.materialIndex//materials.indexOf(meshMaterial)
                            currentEntity.run {
                                materialIndex = targetMaterialIndex
                                update = Update.STATIC.value
                                meshBufferIndex = entityBufferIndex
                                entityIndex = entityId
                                meshIndex = targetMeshIndex
                                baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
                                baseJointIndex = allocation.baseJointIndex
                                animationFrame0 = animationFrame
                                isInvertedTexCoordY = if (model.isInvertTexCoordY) 1 else 0
                                dummy4 = allocation.indexOffset
                                val boundingVolume = modelCacheComponent.meshSpatials[targetMeshIndex].boundingVolume

                                setTrafoAndBoundingVolume(transform.transformation, boundingVolume)
                            }
                            entityBufferIndex++
                        }
                    }
                }
            }
        }
    }

    private fun updateGpuEntitiesArray() {
        gpuEntitiesArray = gpuEntitiesArray.enlarge(requiredEntityBufferSize)
    }

    private var requiredEntityBufferSize: Int = 0
    private fun calculateRequiredEntityBufferSize(): Int = mapEntity { parentEntityId ->
        val modelComponentOrNull = modelComponentMapper.getOrNull(parentEntityId)
        if(modelComponentOrNull != null) {

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances

            val instanceCount = entityIds.size
            instanceCount * (modelCache[modelComponentOrNull.modelComponentDescription]?.meshes?.size ?: 0)
        } else 0
    }.sum()

    private fun updateGpuJointsArray() = with(gpuJointsArray.byteBuffer) {
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)

        for ((index, joint) in joints.withIndex()) {
            gpuJointsArray[index].set(joint)
        }
    }

    fun cacheEntityIndices() {
        entityIndices.clear()
        var instanceIndex = 0
        forEachEntity { parentEntityId ->
            val modelComponent = modelComponentMapper.get(parentEntityId)
            entityIndices[parentEntityId] = instanceIndex

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances

            val instanceCount = entityIds.size
            val meshCount = modelCache[modelComponent.modelComponentDescription]!!.meshes.size

            instanceIndex += meshCount * instanceCount
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
        return allocate(model.uniqueVertices.size, model.indices.capacity() / Integer.BYTES)
    }

    fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexBuffer.VertexIndexOffsets): List<VertexIndexBuffer.VertexIndexOffsets> {
        var currentIndexOffset = vertexIndexOffsets.indexOffset
        var currentVertexOffset = vertexIndexOffsets.vertexOffset

        val model = modelCache[modelComponentDescription]!!

        return model.meshes.indices.map { i ->
            val mesh = model.meshes[i] as Mesh<*>
            VertexIndexBuffer.VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
                currentIndexOffset += mesh.indexBufferValues.capacity() / Integer.BYTES
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
                else -> throw IllegalStateException("Hello compiler bug")
            } // TODO: sealed classes!!

            indexBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, model.indices)

            return vertexIndexOffsetsForMeshes
        }
    }

    override fun extract(currentWriteState: RenderState) {
        cacheEntityIndices() // TODO: Don't do this here, on insert/remove should be sufficient
        currentWriteState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        currentWriteState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated

        currentWriteState.entitiesState.jointsBuffer.ensureCapacityInBytes(gpuJointsArray.byteBuffer.capacity())
        currentWriteState.entitiesState.entitiesBuffer.ensureCapacityInBytes(gpuEntitiesArray.byteBuffer.capacity())
        gpuJointsArray.byteBuffer.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer)
        gpuEntitiesArray.byteBuffer.copyTo(currentWriteState.entitiesBuffer.buffer)

        extract(
            currentWriteState.camera, currentWriteState, currentWriteState.camera.getPosition(),
            config.debug.isDrawLines, allocations, entityIndices,
        )
    }

    fun extract(
        camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, drawLines: Boolean,
        allocations: MutableMap<ModelComponentDescription, Allocation>,
        entityIndices: MutableMap<Int, Int>
    ) {

        currentWriteState.entitiesState.renderBatchesStatic.clear()
        currentWriteState.entitiesState.renderBatchesAnimated.clear()

        var batchIndex = 0
        forEachEntity { parentEntityId ->
            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances
            val instanceCount = entityIds.size

            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)

            val entityId = parentEntityId

            val instanceComponent = instanceComponentMapper.getOrNull(entityId)
            val transformComponent = transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(instanceComponent!!.targetEntity)
            val transform = transformComponent!!.transform

            if (modelComponent != null) {
                val materialComponentOrNull = materialComponentMapper.getOrNull(entityId) ?: instanceComponent?.targetEntity?.let { targetEntity ->
                    materialComponentMapper.getOrNull(targetEntity)
                }
                val entityVisible = !invisibleComponentMapper.has(entityId)

                val entityIndexOf = entityIndices[parentEntityId]!! // TODO: Factor in instance index here

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
                        (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, entityIndexOf, -1)) { (_, _) -> RenderBatch() }
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
                        drawElementsIndirectCommand.instanceCount = instanceCount
                        drawElementsIndirectCommand.count = model.meshIndexCounts[meshIndex]
                        drawElementsIndirectCommand.firstIndex = allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].indexOffset
                        drawElementsIndirectCommand.baseVertex = allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].vertexOffset
                        this.animated = !model.isStatic
                        val meshMaterial = materialComponentOrNull?.material ?: mesh.material // TODO: Think about override per mesh instead of all at once
                        material = meshMaterial
                        program = meshMaterial.programDescription?.let { programCache[it]!! }
                        entityIndex = entityIndexOf //TODO: check if correct index, it got out of hand
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
            }
            batchIndex++
        }
    }

    override fun onLinkEstablished(sourceId: Int, targetId: Int) {
        instancesComponentMapper.create(targetId).apply {
            instances.add(sourceId)
        }
        val transform = transformComponentMapper.getOrNull(sourceId)?.transform ?: transformComponentMapper[targetId].transform
        spatialComponentMapper.getOrNull(sourceId)?.spatial?.recalculate(transform.transformation)
        val parentModelCacheComponent = modelCacheComponentMapper.getOrNull(targetId)
        if(parentModelCacheComponent != null) {
            modelCacheComponentMapper.create(sourceId).apply {
                model = parentModelCacheComponent.model
                allocation = parentModelCacheComponent.allocation
                meshSpatials = List(parentModelCacheComponent.meshSpatials.size) {
                    val parentBoundingVolume = parentModelCacheComponent.meshSpatials[it].boundingVolume
                    StaticTransformSpatial(transform, AABB(parentBoundingVolume.localMin, parentBoundingVolume.localMax))
                }
            }
        }
        requiredEntityBufferSize = calculateRequiredEntityBufferSize()
        cacheEntityIndices()
    }

    override fun onLinkKilled(sourceId: Int, targetId: Int) {
        instancesComponentMapper.getOrNull(targetId)?.instances?.remove(sourceId)
    }

    override fun onTargetDead(sourceId: Int, deadTargetId: Int) {
        instancesComponentMapper.getOrNull(deadTargetId)?.instances?.remove(sourceId)
    }

    override fun onTargetChanged(sourceId: Int, targetId: Int, oldTargetId: Int) {
        instancesComponentMapper[oldTargetId].instances.remove(sourceId)
        onLinkEstablished(sourceId, targetId)
    }
}

fun Model<*>.resolveMaterial(
    materialComponentOrNull: MaterialComponent?
): Material = materialComponentOrNull?.material ?: materials.first()

fun Model<*>.resolveMaterial(
    materialOrNull: Material?
): Material = materialOrNull ?: materials.first()

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