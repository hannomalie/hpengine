package de.hanno.hpengine.engine.model

import EntityStruktImpl.Companion.type
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.EntityStrukt
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.clusters
import de.hanno.hpengine.engine.instancing.instanceCount
import de.hanno.hpengine.engine.instancing.instances
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.Array
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.enlarge
import org.lwjgl.BufferUtils
import struktgen.TypedBuffer
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(
    val manager: ModelComponentManager,
    val materialManager: MaterialManager,
    val gpuContext: GpuContext<OpenGl>,
    val config: Config
) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    val vertexIndexBufferStatic = VertexIndexBuffer(gpuContext, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(gpuContext, 10)

    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()

    val allocations: MutableMap<ModelComponent, Allocation> = mutableMapOf()

    private val _components = CopyOnWriteArrayList<ModelComponent>()
    private var gpuJointsArray = StructArray(size = 1000) { Matrix4f() }

    private val batchingSystem = BatchingSystem()
    var gpuEntitiesArray = TypedBuffer(BufferUtils.createByteBuffer(EntityStrukt.type.sizeInBytes * 1000), EntityStrukt.type)
    val entityIndices: MutableMap<ModelComponent, Int> = mutableMapOf()

    override val components: List<ModelComponent>
        get() = _components

    val ModelComponent.entityIndex
        get() = entityIndices[this]!!

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        for (component in components) {
            component.update(scene, deltaSeconds)
        }
        cacheEntityIndices()
        updateGpuEntitiesArray(scene)
        updateGpuJointsArray()

        var counter = 0
        val materials = materialManager.materials

        for(modelComponent in components) {
            if(counter >= gpuEntitiesArray.size) { throw IllegalStateException("More model components then size of gpu entities array") }

            val allocation = allocations[modelComponent]!!

            val meshes = modelComponent.meshes
            val entity = modelComponent.entity

            var currentEntity = gpuEntitiesArray[counter]
            val entityBufferIndex = scene.entityManager.run { modelComponent.entityIndex }

            gpuEntitiesArray.byteBuffer.run {
                for ((targetMeshIndex, mesh) in meshes.withIndex()) {
                    val targetMaterialIndex = materials.indexOf(mesh.material)
                    currentEntity.run {
                        materialIndex = targetMaterialIndex
                        update = entity.updateType.asDouble.toInt()
                        meshBufferIndex = entityBufferIndex + targetMeshIndex
                        entityIndex = entity.index
                        meshIndex = targetMeshIndex
                        baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
                        baseJointIndex = allocation.baseJointIndex
                        animationFrame0 = modelComponent.animationFrame0
                        isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
                        val boundingVolume = modelComponent.getBoundingVolume(entity.transform, mesh)
                        dummy4 = allocation.indexOffset
                        setTrafoAndBoundingVolume(entity.transform.transformation, boundingVolume)
                    }
                    counter++
                    currentEntity = gpuEntitiesArray[counter]

                    for(cluster in entity.clusters) {
                        // TODO: This is so lame, but for some reason extraction has to be done twice. investigate here!
//                    if(cluster.updatedInCycle == -1L || cluster.updatedInCycle == 0L || cluster.updatedInCycle >= scene.currentCycle) {
                        for (instance in cluster) {
                            currentEntity = gpuEntitiesArray[counter]
                            val instanceMatrix = instance.transform.transformation
                            val instanceMaterialIndex = if(instance.materials.isEmpty()) targetMaterialIndex else materials.indexOf(instance.materials[targetMeshIndex])

                            currentEntity.run {
                                materialIndex = instanceMaterialIndex
                                update = entity.updateType.ordinal
                                meshBufferIndex = entityBufferIndex + targetMeshIndex
                                entityIndex = entity.index
                                meshIndex = targetMeshIndex
                                baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
                                baseJointIndex = allocation.baseJointIndex
                                animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
                                isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
                                dummy4 = allocation.indexOffset
                                setTrafoAndBoundingVolume(instanceMatrix, instance.spatial.boundingVolume)
                            }

                            counter++
                        }
                        cluster.updatedInCycle++
                    }

                    // TODO: This has to be the outer loop i think?
                    if (entity.hasParent) {
                        for (instance in entity.instances) {
                            val instanceMatrix = instance.transform.transformation

                            currentEntity.run {
                                materialIndex = targetMaterialIndex
                                update = entity.updateType.ordinal
                                meshBufferIndex = entityBufferIndex + targetMeshIndex
                                entityIndex = entity.index
                                meshIndex = targetMeshIndex
                                baseVertex = allocation.forMeshes.first().vertexOffset
                                baseJointIndex = allocation.baseJointIndex
                                animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
                                isInvertedTexCoordY = if(modelComponent.isInvertTexCoordY) 1 else 0
                                val boundingVolume = instance.spatial.boundingVolume
                                setTrafoAndBoundingVolume(instanceMatrix, boundingVolume)
                            }

                            counter++
                            currentEntity = gpuEntitiesArray[counter]
                        }
                    }
                }
            }
        }
    }

    private fun getRequiredEntityBufferSize(): Int {
        return components.sumBy { it.entity.instanceCount * it.meshes.size }
    }
    private fun updateGpuEntitiesArray(scene: Scene) {
        gpuEntitiesArray = gpuEntitiesArray.enlarge(getRequiredEntityBufferSize())
        gpuEntitiesArray.byteBuffer.rewind()
    }

    override fun addComponent(component: ModelComponent) {
        allocateVertexIndexBufferSpace(listOf(component.entity))
        _components.add(component)

        // TODO: Implement for reload feature
        // FileMonitor.addOnFileChangeListener(component.model.file) { }
    }

    private fun updateGpuJointsArray() {
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)
        gpuJointsArray.buffer.rewind()

        for((index, joint) in joints.withIndex()) {
            val target = gpuJointsArray.getAtIndex(index)
            target.set(joint)
        }
    }

    sealed class Allocation(val forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>) {
        init {
            require(forMeshes.isNotEmpty())
        }
        val indexOffset = forMeshes.first().indexOffset
        val vertexOffset = forMeshes.first().vertexOffset

        class Static(forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>): Allocation(forMeshes)
        class Animated(forMeshes: List<VertexIndexBuffer.VertexIndexOffsets>, val jointsOffset: Int): Allocation(forMeshes)
    }

    fun allocateVertexIndexBufferSpace(entities: List<Entity>) {
        val components = entities.flatMap { it.components.filterIsInstance<ModelComponent>() }
        val allocations = components.associateWith { c ->
            if (c.model.isStatic) {
                val vertexIndexBuffer = vertexIndexBufferStatic
                val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                val vertexIndexOffsetsForMeshes = c.putToBuffer(
                    gpuContext,
                    vertexIndexBuffer,
                    vertexIndexOffsets
                )
                Allocation.Static(vertexIndexOffsetsForMeshes)
            } else {
                val vertexIndexBuffer = vertexIndexBufferAnimated
                val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                val vertexIndexOffsetsForMeshes = c.putToBuffer(
                    gpuContext,
                    vertexIndexBuffer,
                    vertexIndexOffsets
                )

                val elements = (c.model as AnimatedModel).animation.frames
                        .flatMap { frame -> frame.jointMatrices.toList() }
                val jointsOffset = joints.size
                joints.addAll(elements)
                Allocation.Animated(vertexIndexOffsetsForMeshes, jointsOffset)
            }
        }

        this.allocations.putAll(allocations)
    }
    override fun clear() {
        _components.clear()

        vertexIndexBufferStatic.resetAllocations()
        vertexIndexBufferAnimated.resetAllocations()
    }

    override fun extract(renderState: RenderState) {
        cacheEntityIndices() // TODO: Move this to update step
        renderState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        renderState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated

//        gpuJointsArray.safeCopyTo(renderState.entitiesState.jointsBuffer)
//        gpuEntitiesArray.safeCopyTo(renderState.entitiesBuffer)

        renderState.entitiesState.jointsBuffer.ensureCapacityInBytes(gpuJointsArray.buffer.capacity())
        renderState.entitiesState.entitiesBuffer.ensureCapacityInBytes(gpuEntitiesArray.byteBuffer.capacity())
        gpuJointsArray.buffer.copyTo(renderState.entitiesState.jointsBuffer.buffer, true)
        gpuEntitiesArray.byteBuffer.copyTo(renderState.entitiesBuffer.buffer, true)

        batchingSystem.extract(renderState.camera, renderState, renderState.camera.getPosition(),
            components, config.debug.isDrawLines,
            allocations, entityIndices)
    }

    fun cacheEntityIndices() {
        entityIndices.clear()
        var index = 0
        for (current in components) {
            entityIndices[current] = index
            index += current.entity.instanceCount * current.meshes.size
        }
    }
}

fun <T> TypedBuffer<T>.enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(size * struktType.sizeInBytes, copyContent)

fun <T> TypedBuffer<T>.enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) = if(byteBuffer.capacity() < sizeInBytes) {
    TypedBuffer(BufferUtils.createByteBuffer(sizeInBytes), struktType).apply {
        if(copyContent) {
            val self = this@apply
            this@enlargeToBytes.byteBuffer.copyTo(self.byteBuffer)
        }
    }
} else this

val ModelComponentSystem.Allocation.baseJointIndex: Int
    get() = when(this) {
        is ModelComponentSystem.Allocation.Static -> 0
        is ModelComponentSystem.Allocation.Animated -> jointsOffset
    }
