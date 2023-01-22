package de.hanno.hpengine.artemis.model

import AnimatedVertexStruktPackedImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.link.LinkListener
import com.artemis.utils.IntBag
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.artemis.instancing.InstancesComponent
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.appendIndices
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.shader.ProgramImpl
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.model.material.MaterialManager
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.scene.*
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.StaticTransformSpatial
import de.hanno.hpengine.transform.TransformSpatial
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import struktgen.api.get
import struktgen.api.typed
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

context(GraphicsApi)
@One(
    ModelComponent::class,
)
class ModelSystem(
    private val config: Config,
    private val textureManager: OpenGLTextureManager,
    private val materialManager: MaterialManager,
    private val programManager: ProgramManager,
    private val entityBuffer: EntityBuffer,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : BaseEntitySystem(), Extractor, LinkListener {
    private val threadPool = Executors.newFixedThreadPool(1)

    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>
    lateinit var spatialComponentMapper: ComponentMapper<SpatialComponent>

    val vertexIndexBufferStatic = VertexIndexBuffer(VertexStruktPacked.type, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(AnimatedVertexStruktPacked.type, 10)

    val joints: MutableList<Matrix4f> = CopyOnWriteArrayList()

    private val allocations: MutableMap<ModelComponentDescription, Allocation> = mutableMapOf()

    private var gpuJointsArray = BufferUtils.createByteBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)

    var gpuEntitiesArray = entityBuffer.underlying
    val entityIndices: MutableMap<Int, Int> = mutableMapOf()
    fun getEntityIdForEntityBufferIndex(entityBufferIndex: Int): Int = gpuEntitiesArray.byteBuffer.run {
        return gpuEntitiesArray[entityBufferIndex].entityIndex
    }

    private val modelCache = mutableMapOf<ModelComponentDescription, Model<*>>()
    private val programCache = mutableMapOf<ProgramDescription, ProgramImpl<FirstPassUniforms>>()
    private val staticModelLoader = StaticModelLoader()
    private val animatedModelLoader = AnimatedModelLoader()

    operator fun get(modelComponentDescription: ModelComponentDescription) = modelCache[modelComponentDescription]

    override fun inserted(entityId: Int) {
        threadPool.submit {
            if (loadModelToCache(entityId) != null) {
                cacheEntityIndices()

                requiredEntityBufferSize = calculateRequiredEntityBufferSize()
            }
        }
    }

    override fun removed(entities: IntBag?) {
        cacheEntityIndices()
    }

    override fun removed(entityId: Int) {
        cacheEntityIndices()
    }

    override fun inserted(entities: IntBag) {
        val entities = IntBag().apply { addAll(entities) }
        threadPool.submit {
            entities.forEach { entityId ->
                loadModelToCache(entityId)
            }

            cacheEntityIndices()

            requiredEntityBufferSize = calculateRequiredEntityBufferSize()
        }
    }

    private fun loadModelToCache(entityId: Int): ModelCacheComponent? {
        val instanceComponent = instanceComponentMapper.getOrNull(entityId)
        val modelComponentOrNull = modelComponentMapper.getOrNull(entityId)
        if (instanceComponent == null && modelComponentOrNull == null) return null

        val transformComponent =
            transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper[instanceComponent!!.targetEntity]
        val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)

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
                            programManager.getFirstPassProgram(
                                programDescription,
                                StaticFirstPassUniforms()
                            ) as ProgramImpl<FirstPassUniforms>
                    }
                }
            }
        }

        world.edit(entityId).run { BoundingVolumeComponent().apply { boundingVolume = model.boundingVolume } }
        return world.edit(entityId).run {
            ModelCacheComponent().apply {
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
                allocateVertexIndexBufferSpace(this, descr)
                add(this)
            }
        }
    }

    override fun processSystem() {
        // TODO: This should not happen here, but in "inserted" methods
        requiredEntityBufferSize = calculateRequiredEntityBufferSize()

        gpuEntitiesArray = gpuEntitiesArray.enlarge(requiredEntityBufferSize)
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)

        var entityBufferIndex = 0

        forEachEntity { parentEntityId ->
            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances
            val entityIds = if (instances == null) arrayOf(parentEntityId) else arrayOf(parentEntityId) + instances

            for (entityId in entityIds) {
                val instanceComponent = instanceComponentMapper.getOrNull(entityId)
                val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)

                if (modelComponent != null) {
                    val modelCacheComponent =
                        modelCacheComponentMapper.getOrNull(entityId) ?: modelCacheComponentMapper[parentEntityId]
                    val modelIsLoaded = modelCacheComponent != null

                    if (modelIsLoaded) {
                        val model = modelCacheComponent.model
                        when (model) {
                            is AnimatedModel -> model.animations.values.forEach {
                                it.update(world.delta)
                            }
                            is StaticModel -> {}
                        }
                        val transformComponent =
                            transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(
                                instanceComponent!!.targetEntity
                            )
                        val transform = transformComponent!!.transform

                        val allocation = modelCacheComponent.allocation

                        val meshes = model.meshes


                        val animationFrame = when (model) {
                            is AnimatedModel -> model.animation.currentFrame
                            is StaticModel -> 0
                        }
                        gpuEntitiesArray.byteBuffer.run {
                            for ((targetMeshIndex, mesh) in meshes.withIndex()) {
                                val currentEntity = gpuEntitiesArray[entityBufferIndex]

                                val meshMaterial = materialComponentOrNull?.material
                                    ?: mesh.material // TODO: Think about override per mesh instead of all at once
                                val targetMaterialIndex = materialManager.indexOf(meshMaterial)
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
                                    val boundingVolume =
                                        modelCacheComponent.meshSpatials[targetMeshIndex].boundingVolume

                                    setTrafoAndBoundingVolume(transform.transformation, boundingVolume)
                                }
                                entityBufferIndex++
                            }
                        }
                    }
                }
            }
        }
        with(gpuJointsArray.byteBuffer) {
            for ((index, joint) in joints.withIndex()) {
                gpuJointsArray[index].set(joint)
            }
        }
    }

    private var requiredEntityBufferSize: Int = 0
    private fun calculateRequiredEntityBufferSize(): Int = mapEntity { parentEntityId ->
        val modelComponentOrNull = modelComponentMapper.getOrNull(parentEntityId)
        if (modelComponentOrNull != null) {

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances

            val instanceCount = entityIds.size
            instanceCount * (modelCache[modelComponentOrNull.modelComponentDescription]?.meshes?.size ?: 0)
        } else 0
    }.sum()

    fun cacheEntityIndices() {
        entityIndices.clear()
        var instanceIndex = 0
        forEachEntity { parentEntityId ->
            entityIndices[parentEntityId] = instanceIndex

            val modelCacheComponent = modelCacheComponentMapper.getOrNull(parentEntityId)
            if (modelCacheComponent != null) {
                val meshCount = modelCacheComponent.model.meshes.size

                val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
                val entityIds = listOf(parentEntityId) + instances

                val instanceCount = entityIds.size
                instanceIndex += meshCount * instanceCount
            }
        }
    }

    fun allocateVertexIndexBufferSpace(modelCacheComponent: ModelCacheComponent, descr: ModelComponentDescription) {
        val allocation = when (val model = modelCacheComponent.model) {
            is AnimatedModel -> {
                val vertexIndexBuffer = vertexIndexBufferAnimated
                val vertexIndexOffsets = vertexIndexBuffer.allocateForModel(model)
                val vertexIndexOffsetsForMeshes = model.putToBuffer(
                    vertexIndexBuffer,
                    vertexIndexBufferAnimated,
                    vertexIndexOffsets
                )

                val elements = model.animation.frames.flatMap { frame -> frame.jointMatrices.toList() }
                val jointsOffset = joints.size
                joints.addAll(elements)
                Allocation.Animated(vertexIndexOffsetsForMeshes, jointsOffset)
            }
            is StaticModel -> {
                val vertexIndexBuffer = vertexIndexBufferStatic
                val vertexIndexOffsets = vertexIndexBuffer.allocateForModel(model)
                val vertexIndexOffsetsForMeshes = model.putToBuffer(
                    vertexIndexBuffer,
                    vertexIndexBufferAnimated,
                    vertexIndexOffsets
                )
                Allocation.Static(vertexIndexOffsetsForMeshes)
            }
        }
        modelCacheComponent.allocation = allocation
        allocations[descr] = allocation
    }

    fun VertexIndexBuffer<*>.allocateForComponent(modelComponent: ModelComponent): VertexIndexOffsets {
        val model = modelCache[modelComponent.modelComponentDescription]!!
        return allocate(model.uniqueVertices.size, model.indices.capacity() / Integer.BYTES)
    }

    fun VertexIndexBuffer<*>.allocateForModel(model: Model<*>): VertexIndexOffsets {
        return allocate(model.uniqueVertices.size, model.indices.capacity() / Integer.BYTES)
    }

    fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
        var currentIndexOffset = vertexIndexOffsets.indexOffset
        var currentVertexOffset = vertexIndexOffsets.vertexOffset

        val model = modelCache[modelComponentDescription]!!

        return model.meshes.indices.map { i ->
            val mesh = model.meshes[i] as Mesh<*>
            VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
                currentIndexOffset += mesh.indexBufferValues.capacity() / Integer.BYTES
                currentVertexOffset += mesh.vertices.size
            }
        }
    }

    fun Model<*>.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
        var currentIndexOffset = vertexIndexOffsets.indexOffset
        var currentVertexOffset = vertexIndexOffsets.vertexOffset

        val model = this

        return model.meshes.indices.map { i ->
            val mesh = model.meshes[i] as Mesh<*>
            VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
                currentIndexOffset += mesh.indexBufferValues.capacity() / Integer.BYTES
                currentVertexOffset += mesh.vertices.size
            }
        }
    }

    fun ModelComponent.putToBuffer(
        vertexIndexBuffer: VertexIndexBuffer<*>,
        vertexIndexBufferAnimated: VertexIndexBuffer<*>,
        vertexIndexOffsets: VertexIndexOffsets
    ): List<VertexIndexOffsets> {
        val model = modelCache[modelComponentDescription]!!
        val (targetBuffer, vertexType) = when (model) {
            is AnimatedModel -> Pair(vertexIndexBufferAnimated, AnimatedVertexStruktPacked.type)
            is StaticModel -> Pair(vertexIndexBuffer, VertexStruktPacked.type)
        }
        return synchronized(targetBuffer) {
            val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)
            targetBuffer.vertexStructArray.addAll(
                vertexIndexOffsets.vertexOffset * vertexType.sizeInBytes,
                model.verticesPacked.byteBuffer
            )
            targetBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, model.indices)
            vertexIndexOffsetsForMeshes
        }
    }

    fun Model<*>.putToBuffer(
        vertexIndexBuffer: VertexIndexBuffer<*>,
        vertexIndexBufferAnimated: VertexIndexBuffer<*>,
        vertexIndexOffsets: VertexIndexOffsets
    ): List<VertexIndexOffsets> {
        val model = this
        val (targetBuffer, vertexType) = when (model) {
            is AnimatedModel -> Pair(vertexIndexBufferAnimated, AnimatedVertexStruktPacked.type)
            is StaticModel -> Pair(vertexIndexBuffer, VertexStruktPacked.type)
        }
        return synchronized(targetBuffer) {
            val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)
            targetBuffer.vertexStructArray.addAll(
                vertexIndexOffsets.vertexOffset * vertexType.sizeInBytes,
                model.verticesPacked.byteBuffer
            )
            targetBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, model.indices)
            vertexIndexOffsetsForMeshes
        }
    }

    override fun extract(currentWriteState: RenderState) {
        cacheEntityIndices() // TODO: Don't do this here, on insert/remove should be sufficient
        currentWriteState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic = vertexIndexBufferStatic
        currentWriteState[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated = vertexIndexBufferAnimated

        currentWriteState[entitiesStateHolder.entitiesState].jointsBuffer.ensureCapacityInBytes(gpuJointsArray.byteBuffer.capacity())
        currentWriteState[entitiesStateHolder.entitiesState].entitiesBuffer.ensureCapacityInBytes(gpuEntitiesArray.byteBuffer.capacity())
        gpuJointsArray.byteBuffer.copyTo(currentWriteState[entitiesStateHolder.entitiesState].jointsBuffer.buffer)
        gpuEntitiesArray.byteBuffer.copyTo(currentWriteState[entitiesStateHolder.entitiesState].entitiesBuffer.buffer)

        val camera = currentWriteState[primaryCameraStateHolder.camera]

        extract(
            camera, currentWriteState, camera.getPosition(),
            config.debug.isDrawLines, allocations, entityIndices,
        )
    }

    fun extract(
        camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, drawLines: Boolean,
        allocations: MutableMap<ModelComponentDescription, Allocation>,
        entityIndices: MutableMap<Int, Int>
    ) {

        currentWriteState[entitiesStateHolder.entitiesState].renderBatchesStatic.clear()
        currentWriteState[entitiesStateHolder.entitiesState].renderBatchesAnimated.clear()

        forEachEntity { parentEntityId ->
            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances
            val instanceCount = entityIds.size

            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)
            val modelCacheComponent = modelCacheComponentMapper.getOrNull(parentEntityId)

            val entityId = parentEntityId

            val instanceComponent = instanceComponentMapper.getOrNull(entityId)
            val transformComponent = transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(
                instanceComponent!!.targetEntity
            )
            val transform = transformComponent!!.transform

            if (modelComponent != null && modelCacheComponent != null) {
                val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)
                    ?: instanceComponent?.targetEntity?.let { targetEntity ->
                        materialComponentMapper.getOrNull(targetEntity)
                    }
                val entityVisible = !invisibleComponentMapper.has(entityId)

                val entityIndexOf = entityIndices[parentEntityId]!! // TODO: Factor in instance index here

                val model: Model<*> = modelCacheComponent.model
                val meshes = model.meshes
                for (meshIndex in meshes.indices) {
                    val mesh = meshes[meshIndex]
                    val meshCenter = mesh.spatial.getCenter(transform.transformation)
                    val boundingSphereRadius = model.getBoundingSphereRadius(mesh)

                    val (min1, max1) = model.getBoundingVolume(transform, mesh)
                    val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                    val meshIsInFrustum =
                        intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

                    val visibleForCamera =
                        meshIsInFrustum// || entity.instanceCount > 1 // TODO: Better culling for instances
                    val meshBufferIndex = entityIndexOf + meshIndex //* entity.instanceCount

                    val batch =
                        (currentWriteState[entitiesStateHolder.entitiesState].cash).computeIfAbsent(
                            BatchKey(
                                mesh,
                                entityIndexOf,
                                -1
                            )
                        ) { (_, _) -> RenderBatch() }
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
                        drawElementsIndirectCommand.firstIndex =
                            allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].indexOffset
                        drawElementsIndirectCommand.baseVertex =
                            allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex].vertexOffset
                        this.animated = !model.isStatic
                        val meshMaterial = materialComponentOrNull?.material
                            ?: mesh.material // TODO: Think about override per mesh instead of all at once
                        material = meshMaterial
                        program = meshMaterial.programDescription?.let { programCache[it]!! }
                        entityIndex = entityIndexOf //TODO: check if correct index, it got out of hand
                        entityName = mesh.name // TODO: use entity name component
                        contributesToGi = true//entity.contributesToGi TODO: reimplement
                        this.meshIndex = meshIndex
                    }

                    if (batch.isStatic) {
                        currentWriteState[entitiesStateHolder.entitiesState].renderBatchesStatic.add(batch)
                    } else {
                        currentWriteState[entitiesStateHolder.entitiesState].renderBatchesAnimated.add(batch)
                    }
                }
            }
        }
    }

    override fun onLinkEstablished(sourceId: Int, targetId: Int) {
        instancesComponentMapper.create(targetId).apply {
            instances.add(sourceId)
        }
        val transform =
            transformComponentMapper.getOrNull(sourceId)?.transform ?: transformComponentMapper[targetId].transform
        spatialComponentMapper.getOrNull(sourceId)?.spatial?.recalculate(transform.transformation)
        val parentModelCacheComponent = modelCacheComponentMapper.getOrNull(targetId)
        if (parentModelCacheComponent != null) {
            modelCacheComponentMapper.create(sourceId).apply {
                model = parentModelCacheComponent.model
                allocation = parentModelCacheComponent.allocation
                meshSpatials = List(parentModelCacheComponent.meshSpatials.size) {
                    val parentBoundingVolume = parentModelCacheComponent.meshSpatials[it].boundingVolume
                    StaticTransformSpatial(
                        transform,
                        AABB(parentBoundingVolume.localMin, parentBoundingVolume.localMax)
                    )
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