package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuEntityStruct
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.enlarge
import de.hanno.struct.shrinkToBytes
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: Engine<*>) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    private val components = CopyOnWriteArrayList<ModelComponent>()

    private var gpuEntitiesArray = StructArray(size = 1000) { GpuEntityStruct() }
    private var gpuJointsArray = StructArray(size = 1000) { Matrix4f() }

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
        components.add(component)
    }

    private fun cacheEntityIndices() {
        if (updateCache) {
            updateCache = false
            entityIndices.clear()
            var index = 0
            for (current in getComponents()) {
                entityIndices.add(index)
                current.entityBufferIndex = index
                index += current.entity.instanceCount * current.meshes.size
            }
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
        val materials = engine.scene.materialManager.materials

        for(modelComponent in components) {
            val vertexIndexOffsets = allocations[modelComponent]!!
            if(counter < gpuEntitiesArray.size) {
                val meshes = modelComponent.meshes
                val entity = modelComponent.entity

                var target = this.gpuEntitiesArray.getAtIndex(counter)

                for ((meshIndex, mesh) in meshes.withIndex()) {
                    val materialIndex = materials.indexOf(mesh.material)
                    target.selected = entity.isSelected
                    target.materialIndex = materialIndex
                    target.update = entity.updateType.asDouble.toInt()
                    target.meshBufferIndex = modelComponent.entityBufferIndex + meshIndex
                    target.entityIndex = entity.index
                    target.meshIndex = meshIndex
                    target.baseVertex = modelComponent.baseVertices[meshIndex]
                    target.baseJointIndex = modelComponent.baseJointIndex
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

                        target.selected = entity.isSelected
                        target.materialIndex = instanceMaterialIndex
                        target.update = entity.updateType.ordinal
                        target.meshBufferIndex = modelComponent.entityBufferIndex + meshIndex
                        target.entityIndex = entity.index
                        target.meshIndex = meshIndex
                        target.baseVertex = modelComponent.baseVertices[meshIndex]
                        target.baseJointIndex = modelComponent.baseJointIndex
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

                            target.selected = entity.isSelected
                            target.materialIndex = materialIndex
                            target.update = entity.updateType.ordinal
                            target.meshBufferIndex = modelComponent.entityBufferIndex + meshIndex
                            target.entityIndex = entity.index
                            target.meshIndex = meshIndex
                            target.baseVertex = vertexIndexOffsets.vertexOffset
                            target.baseJointIndex = modelComponent.baseJointIndex
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

    val allocations: MutableMap<ModelComponent, VertexIndexBuffer.VertexIndexOffsets> = mutableMapOf()

    fun allocateVertexIndexBufferSpace(entities: List<Entity>) {
        val components = entities.flatMap { it.components.values.filterIsInstance<ModelComponent>() }
        val allocations = components.associateWith { c ->
            if (c.model.isStatic) {
                val vertexIndexBuffer = engine.renderManager.vertexIndexBufferStatic
                val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                c.putToBuffer(engine.gpuContext, vertexIndexBuffer, ModelComponent.DEFAULTCHANNELS, vertexIndexOffsets)
                vertexIndexOffsets
            } else {
                val vertexIndexBuffer = this.engine.renderManager.vertexIndexBufferAnimated
                val vertexIndexOffsets = vertexIndexBuffer.allocateForComponent(c)
                c.putToBuffer(engine.gpuContext, vertexIndexBuffer, ModelComponent.DEFAULTANIMATEDCHANNELS, vertexIndexOffsets)

                c.jointsOffset = joints.size // TODO: Proper allocation
                val elements = (c.model as AnimatedModel).frames
                        .flatMap { frame -> frame.jointMatrices.toList() }
                joints.addAll(elements)
                vertexIndexOffsets
            }
        }.apply {
            updateCache = true
        }

        this.allocations.putAll(allocations)
    }
    override fun clear() = components.clear()

    override fun CoroutineScope.onEntityAdded(entities: List<Entity>): MutableMap<Class<out Component>, Component> {
        val result = onEntityAddedImpl(entities)
        allocateVertexIndexBufferSpace(entities)
        updateCache = true
        return result
    }

    override fun extract(renderState: RenderState) {
        renderState.entitiesBuffer.sizeInBytes = getRequiredEntityBufferSize() * GpuEntityStruct.getBytesPerInstance()
        gpuEntitiesArray = gpuEntitiesArray.shrinkToBytes(renderState.entitiesBuffer.buffer.capacity())
        gpuEntitiesArray.copyTo(renderState.entitiesBuffer.buffer)

        renderState.entitiesState.jointsBuffer.sizeInBytes = joints.size * BufferableMatrix4f.getBytesPerInstance()
        gpuJointsArray = gpuJointsArray.shrinkToBytes(renderState.entitiesState.jointsBuffer.buffer.capacity())
        gpuJointsArray.copyTo(renderState.entitiesState.jointsBuffer.buffer)

//        TODO: Remove this with proper extraction
        renderState.entitiesState.joints = joints
    }
}

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java)?.getInstances() ?: emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java)?.getClusters() ?: emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java)?.getInstanceCount() ?: 1