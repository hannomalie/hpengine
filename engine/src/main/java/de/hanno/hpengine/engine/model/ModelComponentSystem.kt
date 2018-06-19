package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EntityChangedMaterialEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.graphics.GpuEntity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.util.commandqueue.FutureCallable
import net.engio.mbassy.listener.Handler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Supplier

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    private val components = CopyOnWriteArrayList<ModelComponent>()
    val gpuEntities = mutableListOf<GpuEntity>()

    override fun getComponents(): List<ModelComponent> = components

    private val entitiesExtractor: Supplier<List<GpuEntity>> = Supplier {
        engine.commandQueue.addCommand(object: FutureCallable<List<GpuEntity>>() {
            override fun execute() = getCurrentGpuEntities()
        }).get()
    }
    private val entitiesConsumer: BiConsumer<RenderState, List<GpuEntity>> = BiConsumer { _, entities -> engine.renderManager.renderState.addCommand({ it.entitiesBuffer.put(0, entities) }) }
    val bufferEntitiesActionRef = engine.renderManager.renderState.registerAction(TripleBuffer.RareAction<List<GpuEntity>>( entitiesExtractor, entitiesConsumer, engine))

    private val dynamicEntitiesExtractor: Supplier<List<GpuEntity>> = Supplier {
        engine.commandQueue.addCommand(object: FutureCallable<List<GpuEntity>>() {
            override fun execute() = getCurrentGpuEntities().filter { it.update == Update.DYNAMIC }
        }).get()
    }
    private val dynamicEntitiesConsumer: BiConsumer<RenderState, List<GpuEntity>> = BiConsumer { renderState, entities ->
        for(entity in entities.filter { it.update == Update.DYNAMIC }) {
            renderState.entitiesState.entitiesBuffer.put(entities.indexOf(entity), entity)
        }
    }
    val bufferDynamicEntitiesActionRef = engine.renderManager.renderState.registerAction(TripleBuffer.Action<List<GpuEntity>>(dynamicEntitiesExtractor, dynamicEntitiesConsumer))

    private val bufferJointsExtractor: Supplier<List<BufferableMatrix4f>> = Supplier { joints }
    private val bufferJointsConsumer = BiConsumer<RenderState, List<BufferableMatrix4f>> { renderState, joints -> renderState.entitiesState.jointsBuffer.put(0, joints) }
    val bufferJointsActionRef = engine.renderManager.renderState.registerAction(TripleBuffer.Action<List<BufferableMatrix4f>>(bufferJointsExtractor, bufferJointsConsumer))

    init {
        engine.eventBus.register(this)
    }

    override fun create(entity: Entity) = ModelComponent(entity)

    override fun update(deltaSeconds: Float) {
        cacheEntityIndices()
        for (component in getComponents()) {
            component.update(deltaSeconds)
        }
        if(engine.getScene().entityManager.staticEntityHasMoved) {
            bufferEntities()
        } else {//if(engine.getScene().entityManager.entityHasMoved) {
            bufferDynamicEntities()
        }
    }

    fun <T: Bufferable> create(entity: Entity, model: Model<T>): ModelComponent {
        val component = ModelComponent(engine, entity, model)
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
            gpuEntities.clear()
            var index = 0
            for (current in getComponents()) {
                gpuEntities.addAll(current.toEntities())
                entityIndices.add(index)
                current.entityBufferIndex = index
                index += current.entity.instanceCount * current.meshes.size
            }
        }
    }

    private fun getCurrentGpuEntities() = getComponents().flatMap { it.toEntities() }

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

    fun bufferEntities() {
        updateCache = true
        bufferEntitiesActionRef.request(engine.renderManager.drawCycle.get())
        bufferJointsActionRef.request(engine.renderManager.drawCycle.get())
    }

    fun bufferDynamicEntities() {
        updateCache = true
        bufferDynamicEntitiesActionRef.request(engine.renderManager.drawCycle.get())
        bufferJointsActionRef.request(engine.renderManager.drawCycle.get())
    }

    @Subscribe
    @Handler
    fun handle(e: SceneInitEvent) {
        bufferEntities()
    }

    @Subscribe
    @Handler
    fun handle(event: EntityChangedMaterialEvent) {
        val entity = event.entity
        //            buffer(entity);
        bufferEntities()
    }

    override fun onEntityAdded(entities: List<Entity>) {
        bufferEntities()
    }
}

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java)?.getInstances() ?: kotlin.collections.emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java)?.getClusters() ?: kotlin.collections.emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java)?.getInstanceCount() ?: 1
