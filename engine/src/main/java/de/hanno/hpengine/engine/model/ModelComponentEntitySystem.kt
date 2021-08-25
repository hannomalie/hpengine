package de.hanno.hpengine.engine.model

import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.clusters
import de.hanno.hpengine.engine.instancing.instanceCount
import de.hanno.hpengine.engine.instancing.instances
import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.copyTo
import org.lwjgl.BufferUtils
import struktgen.typed
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentEntitySystem(
    val materialManager: MaterialManager,
    val entityManager: EntityManager,
    val gpuContext: GpuContext<OpenGl>,
    val config: Config,
    entityBuffer: EntityBuffer): SimpleEntitySystem(listOf(ModelComponent::class.java)) {

    val vertexIndexBufferStatic = VertexIndexBuffer(gpuContext, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(gpuContext, 10)

    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()

    val allocations: MutableMap<ModelComponent, ModelComponentEntitySystem.Allocation> = mutableMapOf()

    private val _components = CopyOnWriteArrayList<ModelComponent>()
    private var gpuJointsArray = BufferUtils.createByteBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)

    private val batchingSystem = BatchingSystem()
    var gpuEntitiesArray = entityBuffer.underlying
    val entityIndices: MutableMap<ModelComponent, Int> = mutableMapOf()

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        for (component in components) {
            component.update(scene, deltaSeconds)
        }
        cacheEntityIndices()
        updateGpuEntitiesArray()
        updateGpuJointsArray()

        var counter = 0
        val materials = materialManager.materials

        for(modelComponent in getComponents(ModelComponent::class.java)) {
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
                        entityIndex = entityManager.entities.indexOf(entity)
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
        return components.filterIsInstance<ModelComponent>().sumBy { it.entity.instanceCount * it.meshes.size }
    }
    private fun updateGpuEntitiesArray() {
        gpuEntitiesArray = gpuEntitiesArray.enlarge(getRequiredEntityBufferSize())
    }

    override fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        super.onEntityAdded(scene, entities)
        entities.flatMap{ it.components }.forEach { onComponentAdded(scene, it) }
    }
    override fun onComponentAdded(scene: Scene, component: Component) {
        super.onComponentAdded(scene, component)
        if(component is ModelComponent) {
            allocateVertexIndexBufferSpace(listOf(component))
        }
    }

    private fun updateGpuJointsArray() {
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)

        for((index, joint) in joints.withIndex()) {
            gpuJointsArray[index].run {
                set(gpuJointsArray.byteBuffer, joint)
            }
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

    fun allocateVertexIndexBufferSpace(components: List<ModelComponent>) {
        val allocations = components.associateWith { c ->
            if (c.model.isStatic) {
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

        renderState.entitiesState.jointsBuffer.ensureCapacityInBytes(gpuJointsArray.byteBuffer.capacity())
        renderState.entitiesState.entitiesBuffer.ensureCapacityInBytes(gpuEntitiesArray.byteBuffer.capacity())
        gpuJointsArray.byteBuffer.copyTo(renderState.entitiesState.jointsBuffer.buffer, true)
        gpuEntitiesArray.byteBuffer.copyTo(renderState.entitiesBuffer.buffer, true)

        batchingSystem.extract(renderState.camera, renderState, renderState.camera.getPosition(),
            getComponents(ModelComponent::class.java), config.debug.isDrawLines,
            allocations, entityIndices)
    }

    fun cacheEntityIndices() {
        entityIndices.clear()
        var index = 0
        for (current in getComponents(ModelComponent::class.java)) {
            entityIndices[current] = index
            index += current.entity.instanceCount * current.meshes.size
        }
    }

    val ModelComponent.entityIndex
        get() = entityIndices[this]!!
}

val ModelComponentEntitySystem.Allocation.baseJointIndex: Int
    get() = when(this) {
        is ModelComponentEntitySystem.Allocation.Static -> 0
        is ModelComponentEntitySystem.Allocation.Animated -> jointsOffset
    }