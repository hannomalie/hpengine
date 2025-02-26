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
)
class EntityBuffer(
    graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
): BaseEntitySystem(), Extractor {
    private val logger = LogManager.getLogger(EntityBuffer::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>

    var entityCount = ElementCount(0)

    var entitiesBuffer = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(EntityStrukt.type.sizeInBytes) * 100).typed(EntityStrukt.type)
    }

    override fun processSystem() {
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[entitiesBuffer].ensureCapacityInBytes(entityCount * SizeInBytes(EntityStrukt.sizeInBytes))
    }
}
