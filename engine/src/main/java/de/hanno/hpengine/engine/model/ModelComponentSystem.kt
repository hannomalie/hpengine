package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileMonitor
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: EngineContext<*>,
                           val materialManager: MaterialManager) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    val vertexIndexBufferStatic = VertexIndexBuffer(engine.gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)
    val vertexIndexBufferAnimated = VertexIndexBuffer(engine.gpuContext, 10, 10, ModelComponent.DEFAULTANIMATEDCHANNELS)

    private val batchingSystem = BatchingSystem()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()

    private val components = CopyOnWriteArrayList<ModelComponent>()

    private var gpuEntitiesArray = StructArray(size = 1000) { EntityStruct() }
    private var gpuJointsArray = StructArray(size = 1000) { Matrix4f() }

    val allocations: MutableMap<ModelComponent, Allocation> = mutableMapOf()
    val entityIndices: MutableMap<ModelComponent, Int> = mutableMapOf()

    override fun getComponents(): List<ModelComponent> = components

    init {
        engine.eventBus.register(this)
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        for (component in getComponents()) {
            with(component) {
                update(deltaSeconds)
            }
        }
        cacheEntityIndices()
        updateGpuEntitiesArray()
        updateGpuJointsArray()
    }

    override fun addComponent(component: ModelComponent) {
        allocateVertexIndexBufferSpace(listOf(component.entity))
        components.add(component)
        cacheEntityIndices()

        // TODO: Implement for reload feature
        // FileMonitor.addOnFileChangeListener(component.model.file) { }
    }

    private fun cacheEntityIndices() {
        entityIndices.clear()
        var index = 0
        for (current in getComponents()) {
            entityIndices[current] = index
            index += current.entity.instanceCount * current.meshes.size
        }
    }

    private fun updateGpuJointsArray() {
        gpuJointsArray = gpuJointsArray.enlarge(joints.size)
        gpuJointsArray.buffer.rewind()

        for((index, joint) in joints.withIndex()) {
            val target = gpuJointsArray.getAtIndex(index)
            target.set(joint)
        }
    }

    private fun updateGpuEntitiesArray() {
        var counter = 0

        gpuEntitiesArray = gpuEntitiesArray.enlarge(getRequiredEntityBufferSize())
        gpuEntitiesArray.buffer.rewind()
        val materials = materialManager.materials

        for(modelComponent in components) {
            val allocation = allocations[modelComponent]!!
            if(counter < gpuEntitiesArray.size) {
                val meshes = modelComponent.meshes
                val entity = modelComponent.entity

                var target = this.gpuEntitiesArray.getAtIndex(counter)
                val entityBufferIndex = entityIndices[modelComponent]!!

                for ((meshIndex, mesh) in meshes.withIndex()) {
                    val materialIndex = materials.indexOf(mesh.material)
                    target.materialIndex = materialIndex
                    target.update = entity.updateType.asDouble.toInt()
                    target.meshBufferIndex = entityBufferIndex + meshIndex
                    target.entityIndex = entity.index
                    target.meshIndex = meshIndex
                    target.baseVertex = allocation.forMeshes[meshIndex].vertexOffset
                    target.baseJointIndex = allocation.baseJointIndex
                    target.animationFrame0 = modelComponent.animationFrame0
                    target.isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
                    val minMax = modelComponent.getMinMax(entity, mesh)
                    target.dummy3 = 1f
                    target.dummy4 = 1f
                    target.setTrafoMinMax(entity.transformation, minMax.min, minMax.max)

                    counter++
                    target = this.gpuEntitiesArray.getAtIndex(counter)

                    for (instance in entity.instances) {
                        val instanceMatrix = instance.transformation
                        val instanceMaterialIndex = if(instance.materials.isEmpty()) materialIndex else materials.indexOf(instance.materials[meshIndex])

                        target.materialIndex = instanceMaterialIndex
                        target.update = entity.updateType.ordinal
                        target.meshBufferIndex = entityBufferIndex + meshIndex
                        target.entityIndex = entity.index
                        target.meshIndex = meshIndex
                        target.baseVertex = allocation.forMeshes[meshIndex].vertexOffset
                        target.baseJointIndex = allocation.baseJointIndex
                        target.animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
                        target.isInvertedTexCoordY = if (modelComponent.isInvertTexCoordY) 1 else 0
                        val minMaxWorld = instance.minMaxWorld
                        target.setTrafoMinMax(instanceMatrix, minMaxWorld.min, minMaxWorld.max)

                        counter++
                        target = this.gpuEntitiesArray.getAtIndex(counter)
                    }

                    // TODO: This has to be the outer loop i think?
                    if (entity.hasParent()) {
                        for (instance in entity.instances) {
                            val instanceMatrix = instance.transformation

                            target.materialIndex = materialIndex
                            target.update = entity.updateType.ordinal
                            target.meshBufferIndex = entityBufferIndex + meshIndex
                            target.entityIndex = entity.index
                            target.meshIndex = meshIndex
                            target.baseVertex = allocation.forMeshes.first().vertexOffset
                            target.baseJointIndex = allocation.baseJointIndex
                            target.animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
                            target.isInvertedTexCoordY = if(modelComponent.isInvertTexCoordY) 1 else 0
                            val minMaxWorld = instance.minMaxWorld
                            target.setTrafoMinMax(instanceMatrix, minMaxWorld.min, minMaxWorld.max)

                            counter++
                            target = this.gpuEntitiesArray.getAtIndex(counter)
                        }
                    }
                }
            }
        }
        gpuEntitiesArray.buffer.rewind()
    }

    private fun getRequiredEntityBufferSize() = components.sumBy { it.entity.instanceCount * it.meshes.size }

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
                val vertexIndexOffsetsForMeshes = c.putToBuffer(engine.gpuContext, vertexIndexBuffer, vertexIndexOffsets)
                Allocation.Static(vertexIndexOffsetsForMeshes)
            } else {
                val vertexIndexBuffer = vertexIndexBufferAnimated
                val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                val vertexIndexOffsetsForMeshes = c.putToBuffer(engine.gpuContext, vertexIndexBuffer, vertexIndexOffsets)

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
        components.clear()

        vertexIndexBufferStatic.resetAllocations()
        vertexIndexBufferAnimated.resetAllocations()
    }

    override fun onEntityAdded(entities: List<Entity>): MutableList<Component> {
        val result = super.onEntityAdded(entities)
        cacheEntityIndices()
        return result
    }

    override fun extract(renderState: RenderState) {
        renderState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        renderState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated

        gpuEntitiesArray.safeCopyTo(renderState.entitiesBuffer)
        gpuJointsArray.safeCopyTo(renderState.entitiesState.jointsBuffer)

        batchingSystem.extract(renderState.camera, renderState, renderState.camera.getPosition(),
                components, engine.config.debug.isDrawLines, allocations, entityIndices)
    }
}

val ModelComponentSystem.Allocation.baseJointIndex: Int
    get() = when(this) {
        is ModelComponentSystem.Allocation.Static -> 0
        is ModelComponentSystem.Allocation.Animated -> jointsOffset
    }

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java)?.getInstances() ?: emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java)?.getClusters() ?: emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java)?.getInstanceCount() ?: 1