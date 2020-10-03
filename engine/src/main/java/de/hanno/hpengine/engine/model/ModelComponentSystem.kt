package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.clusters
import de.hanno.hpengine.engine.instancing.instances
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: EngineContext,
                           val manager: ModelComponentManager,
                           val materialManager: MaterialManager) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    val vertexIndexBufferStatic = VertexIndexBuffer(engine.gpuContext, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(engine.gpuContext, 10)

    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()

    private val components = CopyOnWriteArrayList<ModelComponent>()

    val allocations: MutableMap<ModelComponent, Allocation> = mutableMapOf()
    private var gpuJointsArray = StructArray(size = 1000) { Matrix4f() }

    override fun getComponents(): List<ModelComponent> = components

    init {
        engine.eventBus.register(this)
    }

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        for (component in getComponents()) {
            with(component) {
                update(scene, deltaSeconds)
            }
        }
        updateGpuJointsArray()

        var counter = 0
        val materials = materialManager.materials
        for(modelComponent in getComponents()) {
            val allocation = allocations[modelComponent]!!
            val gpuEntitiesArray = scene.entityManager.gpuEntitiesArray
            if(counter < gpuEntitiesArray.size) {
                val meshes = modelComponent.meshes
                val entity = modelComponent.entity

                var target = gpuEntitiesArray.getAtIndex(counter)
                val entityBufferIndex = scene.entityManager.run { modelComponent.entityIndex }

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
                    val boundingVolume = modelComponent.getBoundingVolume(entity.transform, mesh)
                    target.dummy4 = allocation.indexOffset
                    target.setTrafoAndBoundingVolume(entity.transform.transformation, boundingVolume)

                    counter++
                    target = gpuEntitiesArray.getAtIndex(counter)

                    for(cluster in entity.clusters) {
                        // TODO: This is so lame, but for some reason extraction has to be done twive. investigate here!
                        if(cluster.updatedInCycle == -1L || cluster.updatedInCycle == 0L || cluster.updatedInCycle >= scene.currentCycle) {
                            for (instance in cluster) {
                                target = gpuEntitiesArray.getAtIndex(counter)
                                val instanceMatrix = instance.transform.transformation
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
                                target.dummy4 = allocation.indexOffset
                                target.setTrafoAndBoundingVolume(instanceMatrix, instance.spatial.boundingVolume)

                                counter++
                            }
                            cluster.updatedInCycle++
                        } else {
                            counter += cluster.size
                        }
                    }

                    // TODO: This has to be the outer loop i think?
                    if (entity.hasParent) {
                        for (instance in entity.instances) {
                            val instanceMatrix = instance.transform.transformation

                            target.materialIndex = materialIndex
                            target.update = entity.updateType.ordinal
                            target.meshBufferIndex = entityBufferIndex + meshIndex
                            target.entityIndex = entity.index
                            target.meshIndex = meshIndex
                            target.baseVertex = allocation.forMeshes.first().vertexOffset
                            target.baseJointIndex = allocation.baseJointIndex
                            target.animationFrame0 = instance.animationController?.currentFrameIndex ?: 0
                            target.isInvertedTexCoordY = if(modelComponent.isInvertTexCoordY) 1 else 0
                            val boundingVolume = instance.spatial.boundingVolume
                            target.setTrafoAndBoundingVolume(instanceMatrix, boundingVolume)

                            counter++
                            target = gpuEntitiesArray.getAtIndex(counter)
                        }
                    }
                }
            }
        }
    }

    override fun addComponent(component: ModelComponent) {
        allocateVertexIndexBufferSpace(listOf(component.entity))
        components.add(component)

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

    override fun extract(renderState: RenderState) {
        renderState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        renderState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated

        gpuJointsArray.safeCopyTo(renderState.entitiesState.jointsBuffer)
    }
}

val ModelComponentSystem.Allocation.baseJointIndex: Int
    get() = when(this) {
        is ModelComponentSystem.Allocation.Static -> 0
        is ModelComponentSystem.Allocation.Animated -> jointsOffset
    }
