package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuEntityStruct
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.struct.ResizableStructArray
import de.hanno.struct.copyTo
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    private val components = CopyOnWriteArrayList<ModelComponent>()

    private val gpuEntitiesArray = ResizableStructArray(size = 1000) { GpuEntityStruct(it) }
    private val gpuJointsArray = ResizableStructArray(size = 1000) { de.hanno.hpengine.engine.math.Matrix4f(it) }

    override fun getComponents(): List<ModelComponent> = components

    init {
        engine.eventBus.register(this)
    }

    override fun create(entity: Entity) = ModelComponent(entity)

    override fun update(deltaSeconds: Float) {
        for (component in getComponents()) {
            component.update(deltaSeconds)
        }
        cacheEntityIndices()
        updateGpuEntitiesArray()
        updateGpuJointsArray()
    }

    fun <T: Bufferable> create(entity: Entity, model: Model<T>): ModelComponent {
        val component = ModelComponent(entity, model)
        components.add(component)
        return component
    }

    override fun addComponent(component: ModelComponent) {
        components.add(component)
        component.componentIndex = getComponents().indexOf(component)
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
        gpuJointsArray.enlarge(joints.size)
        gpuJointsArray.buffer.rewind()

        for((index, joint) in joints.withIndex()) {
            val target = gpuJointsArray.getAtIndex(index)
            target.set(joint)
        }
    }

    private fun updateGpuEntitiesArray() {
        var counter = 0

        gpuEntitiesArray.enlarge(getRequiredEntityBufferSize())
        gpuEntitiesArray.buffer.rewind()

        for(modelComponent in components) {
            if(counter < gpuEntitiesArray.size) {
                val meshes = modelComponent.meshes
                val entity = modelComponent.entity

                var target = this.gpuEntitiesArray.getAtIndex(counter)

                for ((meshIndex, mesh) in meshes.withIndex()) {
                    val materialIndex = mesh.material.materialIndex // TODO: This is broken
                    target.selected = entity.isSelected
                    target.materialIndex = materialIndex
                    target.update = entity.update.asDouble.toInt()
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
                        val instanceMaterialIndex = instance.materials[meshIndex].materialIndex

                        target.selected = entity.isSelected
                        target.materialIndex = instanceMaterialIndex
                        target.update = entity.update.ordinal
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
                            target.update = entity.update.ordinal
                            target.meshBufferIndex = modelComponent.entityBufferIndex + meshIndex
                            target.entityIndex = entity.index
                            target.meshIndex = meshIndex
                            target.baseVertex = modelComponent.baseVertex
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

    fun allocateVertexIndexBufferSpace(entities: List<Entity>) {
        entities.forEach { e ->
            e.getComponents().values.forEach { c ->
                if(c is ModelComponent) {
                    if (c.model.isStatic) {
                        val vertexIndexBuffer = engine.renderManager.vertexIndexBufferStatic
                        c.putToBuffer(engine.gpuContext, vertexIndexBuffer, ModelComponent.DEFAULTCHANNELS)
                    } else {
                        val vertexIndexBuffer = this.engine.renderManager.vertexIndexBufferAnimated
                        c.putToBuffer(engine.gpuContext, vertexIndexBuffer, ModelComponent.DEFAULTANIMATEDCHANNELS)

                        c.jointsOffset = joints.size // TODO: Proper allocation
                        val elements = (c.model as AnimatedModel).frames
                                .flatMap { frame -> frame.jointMatrices.toList() }
                        joints.addAll(elements)
                    }
                }
            }
            updateCache = true
        }
    }
    override fun clear() = components.clear()

    override fun onEntityAdded(entities: List<Entity>) {
        allocateVertexIndexBufferSpace(entities)
        updateCache = true
    }

    fun copyGpuBuffers(currentWriteState: RenderState) {
        currentWriteState.entitiesBuffer.sizeInBytes = getRequiredEntityBufferSize() * GpuEntityStruct.getBytesPerInstance()
        gpuEntitiesArray.shrinkToBytes(currentWriteState.entitiesBuffer.buffer.capacity())
        gpuEntitiesArray.copyTo(currentWriteState.entitiesBuffer.buffer)

        currentWriteState.entitiesState.jointsBuffer.sizeInBytes = joints.size * BufferableMatrix4f.getBytesPerInstance()
        gpuJointsArray.shrinkToBytes(currentWriteState.entitiesState.jointsBuffer.buffer.capacity())
        gpuJointsArray.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer)

    }
}

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java)?.getInstances() ?: kotlin.collections.emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java)?.getClusters() ?: kotlin.collections.emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java)?.getInstanceCount() ?: 1