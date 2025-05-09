package de.hanno.hpengine.spatial

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.model.ModelCacheComponent
import de.hanno.hpengine.scene.MinMax
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, WorldAABB::class, Extractor::class])
@All(TransformComponent::class, ModelCacheComponent::class)
class WorldAABB(
    private val worldAABBStateHolder: WorldAABBStateHolder,
): BaseEntitySystem(), Extractor {
    private val logger = LogManager.getLogger(WorldAABB::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var transformComponentComponentMapper: ComponentMapper<TransformComponent>

    val aabb = AABBData(Vector3f(-100f), Vector3f(100f)) // TODO: Find sensible default?

    override fun inserted(entityId: Int) {
        logger.info("Entity added: $entityId")
        val newAABB = modelCacheComponentMapper[entityId].model.boundingVolume.apply {
            recalculate(transformComponentComponentMapper[entityId].transform.transformation)
        }
        aabb.updateWith(newAABB)
        aabb.update()
        logger.info("Recalculated world aabb")
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[worldAABBStateHolder.worldAABBState].min.set(aabb.min)
        currentWriteState[worldAABBStateHolder.worldAABBState].max.set(aabb.max)
    }
    override fun processSystem() { }
}

private fun AABBData.updateWith(other: AABB) {
    min.x = minOf(min.x, other.min.x)
    min.y= minOf(min.y, other.min.y)
    min.z = minOf(min.z, other.min.z)

    max.x = maxOf(max.x, other.max.x)
    max.y= maxOf(max.y, other.max.y)
    max.z = maxOf(max.z, other.max.z)
}

@Single
class WorldAABBStateHolder(
    renderStateContext: RenderStateContext
) {
    val worldAABBState = renderStateContext.renderState.registerState {
        MinMax(min = Vector3f(), max = Vector3f())
    }
}
