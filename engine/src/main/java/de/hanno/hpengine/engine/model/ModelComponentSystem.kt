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
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.util.Util
import net.engio.mbassy.listener.Handler
import org.joml.Matrix4f
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

data class GpuEntity(val trafo: Matrix4f,
                     val selected: Boolean,
                     val materialIndex: Int,
                     val update: Update,
                     val meshBufferIndex: Int,
                     val entityIndex: Int,
                     val meshIndex: Int,
                     val baseVertex: Int,
                     val baseJointIndex: Int,
                     val animationFrame0: Int,
                     val isInvertedTexCoordY: Boolean,
                     val aabb: AABB) : Bufferable {
    override fun getBytesPerObject(): Int {
        return getBytesPerInstance()
    }

    override fun putToBuffer(buffer: ByteBuffer) {
        buffer.putFloat(trafo.m00())
        buffer.putFloat(trafo.m01())
        buffer.putFloat(trafo.m02())
        buffer.putFloat(trafo.m03())
        buffer.putFloat(trafo.m10())
        buffer.putFloat(trafo.m11())
        buffer.putFloat(trafo.m12())
        buffer.putFloat(trafo.m13())
        buffer.putFloat(trafo.m20())
        buffer.putFloat(trafo.m21())
        buffer.putFloat(trafo.m22())
        buffer.putFloat(trafo.m23())
        buffer.putFloat(trafo.m30())
        buffer.putFloat(trafo.m31())
        buffer.putFloat(trafo.m32())
        buffer.putFloat(trafo.m33())

        buffer.putInt(if (selected) 1 else 0)
        buffer.putInt(materialIndex)
        buffer.putInt(update.asDouble.toInt())
        buffer.putInt(meshBufferIndex)

        buffer.putInt(entityIndex)
        buffer.putInt(meshIndex)
        buffer.putInt(baseVertex)
        buffer.putInt(baseJointIndex)

        buffer.putInt(animationFrame0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putInt(if (isInvertedTexCoordY) 1 else 0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putFloat(aabb.min.x)
        buffer.putFloat(aabb.min.y)
        buffer.putFloat(aabb.min.z)
        buffer.putFloat(1f)

        buffer.putFloat(aabb.max.x)
        buffer.putFloat(aabb.max.y)
        buffer.putFloat(aabb.max.z)
        buffer.putFloat(1f)
    }



    companion object {
        fun getBytesPerInstance(): Int {
            return 16 * java.lang.Float.BYTES + 16 * Integer.BYTES + 8 * java.lang.Float.BYTES
        }
    }
}

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    private val entityIndices = IntArrayList()
    val joints: MutableList<BufferableMatrix4f> = CopyOnWriteArrayList()
    @Transient
    var updateCache = true

    private val components = CopyOnWriteArrayList<ModelComponent>()
    private val gpuEntities = mutableListOf<GpuEntity>()

    override fun getComponents(): List<ModelComponent> = components

    val bufferEntitiesActionRef = engine.renderManager.renderState.registerAction({ renderState ->
//        engine.gpuContext.execute {
            renderState.entitiesState.entitiesBuffer.put(*Util.toArray(gpuEntities, GpuEntity::class.java))
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

            renderState.entitiesState.entitiesBuffer.buffer.position(0)
//        }
    })

    val bufferDynamicEntitiesActionRef = engine.renderManager.renderState.registerAction{ renderState ->
        for(entity in gpuEntities.filter { it.update == Update.DYNAMIC }) {
            renderState.entitiesState.entitiesBuffer.put(gpuEntities.indexOf(entity), entity)
        }
        renderState.entitiesState.entitiesBuffer.buffer.position(0)
    }

    val bufferJointsActionRef = engine.renderManager.renderState.registerAction({ renderState ->
        val array = Util.toArray(engine.getScene().modelComponentSystem.joints, BufferableMatrix4f::class.java)
        renderState.entitiesState.jointsBuffer.put(*array)
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
