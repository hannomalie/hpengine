package de.hanno.hpengine.model

import EntityStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.link.LinkListener
import com.artemis.utils.IntBag
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.instancing.InstancesComponent
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.toCount
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.org.jline.terminal.Size
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.get
import struktgen.api.typed

@Single(binds = [BaseSystem::class, Extractor::class])
@One(
    ModelComponent::class,
    ModelCacheComponent::class,
    InstancesComponent::class,
)
class EntityBuffer(
    graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
): BaseEntitySystem(), Extractor, LinkListener {
    private val logger = LogManager.getLogger(EntityBuffer::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>

    var entityCount = ElementCount(0)

    var entitiesBuffer = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(EntityStrukt.type.sizeInBytes) * 100).typed(EntityStrukt.type)
    }
    val entityIndices: MutableMap<Int, Int> = mutableMapOf()

    fun add(entityId: Int, instancesCount: Int) {
        entityIndices[entityId] = instancesCount
    }

    fun cacheEntityIndices() {
        entityIndices.clear()
        var instanceIndex = 0
        forEachEntity { parentEntityId ->
            entityIndices[parentEntityId] = instanceIndex

            val modelCacheComponent = modelCacheComponentMapper.getOrNull(parentEntityId)
            if (modelCacheComponent != null) {
                val meshCount = modelCacheComponent.model.meshes.size

                val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
                val entityIds = listOf(parentEntityId) + instances

                val instanceCount = entityIds.size
                instanceIndex += meshCount * instanceCount
            }
        }
        entityCount = instanceIndex.toCount()
    }

    fun getEntityIdForEntityBufferIndex(entityBufferIndex: Int): Int {
        val buffer = renderStateContext.renderState.currentReadState[entitiesBuffer]
        buffer.byteBuffer.run {
            return buffer[entityBufferIndex].entityIndex
        }
    }
    fun getEntityIndex(entityId: Int): Int? = entityIndices[entityId]

    override fun removed(entities: IntBag?) {
        cacheEntityIndices()
    }

    override fun removed(entityId: Int) {
        cacheEntityIndices()
    }

    override fun inserted(entityId: Int) {
        cacheEntityIndices()
    }

    override fun processSystem() {
    }

    override fun onLinkEstablished(sourceId: Int, targetId: Int) {
        cacheEntityIndices()
    }

    override fun onLinkKilled(sourceId: Int, targetId: Int) {
        cacheEntityIndices()
    }

    override fun onTargetDead(sourceId: Int, deadTargetId: Int) {
        cacheEntityIndices()
    }

    override fun onTargetChanged(sourceId: Int, targetId: Int, oldTargetId: Int) {
        cacheEntityIndices()
    }

    override fun extract(currentWriteState: RenderState) {
        cacheEntityIndices() // TODO: Don't do this here, on insert/remove should be sufficient
        currentWriteState[entitiesBuffer].ensureCapacityInBytes(entityCount * SizeInBytes(EntityStrukt.sizeInBytes))
    }
}
