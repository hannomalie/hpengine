package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.EntityChangedMaterialEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.graphics.GpuEntity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import net.engio.mbassy.listener.Handler
import java.util.concurrent.CopyOnWriteArrayList

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    override val componentClass: Class<ModelComponent> = ModelComponent::class.java

    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    private val components = CopyOnWriteArrayList<ModelComponent>()
    private val gpuEntities = mutableListOf<GpuEntity>()

    override fun getComponents(): List<ModelComponent> = components

    val bufferEntitiesActionRef = engine.renderManager.renderState.registerAction({ renderState ->
            renderState.entitiesState.entitiesBuffer.put(0, gpuEntities)
            renderState.entitiesState.entitiesBuffer.buffer.position(0)

//            for(entity in engine.getScene().getEntities().filter { it.hasComponent(ModelComponent.COMPONENT_KEY) }) {
//                val modelComponent = entity.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)
//                for(mesh in modelComponent.meshes) {
//                    for(i in 0 .. entity.instanceCount) {
//                        System.out.println("Entity " + entity.name + " - Mesh " + mesh.name + " - Instance " + i)
//                        ModelComponent.debugPrintFromBufferStatic(renderState.entitiesState.entitiesBuffer.buffer)
//                    }
//                }
//            }

    })

    val bufferDynamicEntitiesActionRef = engine.renderManager.renderState.registerAction{ renderState ->
        for(entity in gpuEntities.filter { it.update == Update.DYNAMIC }) {
            renderState.entitiesState.entitiesBuffer.put(gpuEntities.indexOf(entity), entity)
        }
        renderState.entitiesState.entitiesBuffer.buffer.position(0)
    }

    val bufferJointsActionRef = engine.renderManager.renderState.registerAction({ renderState ->
        renderState.entitiesState.jointsBuffer.put(0, engine.getScene().modelComponentSystem.joints)
    })

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

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        bufferEntities()
    }

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
}

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java, ClustersComponent.clustersComponentType)?.getInstances() ?: kotlin.collections.emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java, ClustersComponent.clustersComponentType)?.getClusters() ?: kotlin.collections.emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java, ClustersComponent.clustersComponentType)?.getInstanceCount() ?: 1
