package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    override val components = mutableListOf<ModelComponent>()

    override fun create(entity: Entity) = ModelComponent(entity)

    override fun update(deltaSeconds: Float) {
        cacheEntityIndices()
        for (component in components) {
            component.update(engine, deltaSeconds)
        }
    }

    fun <T: Bufferable> create(entity: Entity, model: Model<T>): ModelComponent {
        val component = ModelComponent(entity, model)
        components.add(component)
        return component
    }

    override fun addComponent(component: ModelComponent) {
        components.add(component)
        component.componentIndex = components.indexOf(component)
    }

    private fun cacheEntityIndices() {
        if (updateCache) {
            updateCache = false
            entityIndices.clear()
            var index = 0
            for (current in components) {
                entityIndices.add(index)
                current.entityBufferIndex = index
                index += current.entity.instanceCount * current.meshes.size
            }
        }
    }

    fun allocateVertexIndexBufferSpace(entities: List<Entity>) {

        entities.forEach { e ->
            e.getComponents().values.forEach { c ->
                if(c is ModelComponent) {

                    if (c.model.isStatic()) {
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
}