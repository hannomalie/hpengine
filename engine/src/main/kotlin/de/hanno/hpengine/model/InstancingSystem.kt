package de.hanno.hpengine.model

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.link.LinkListener
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.instancing.InstancesComponent
import de.hanno.hpengine.spatial.SpatialComponent
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.StaticTransformSpatial
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, InstancingSystem::class])
@One(
    InstanceComponent::class,
)
class InstancingSystem(
) : BaseEntitySystem(), LinkListener {
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>
    lateinit var spatialComponentMapper: ComponentMapper<SpatialComponent>

    override fun processSystem() {
    }

    override fun onLinkEstablished(sourceId: Int, targetId: Int) {
        instancesComponentMapper.create(targetId).apply {
            instances.add(sourceId)
        }
        val transform = transformComponentMapper.getOrNull(sourceId)?.transform ?: transformComponentMapper[targetId].transform
        spatialComponentMapper.getOrNull(sourceId)?.spatial?.recalculate(transform.transformation)
        val parentModelCacheComponent = modelCacheComponentMapper.getOrNull(targetId)
        if (parentModelCacheComponent != null) {
            modelCacheComponentMapper.create(sourceId).apply {
                model = parentModelCacheComponent.model
                allocation = parentModelCacheComponent.allocation
                meshSpatials = List(parentModelCacheComponent.meshSpatials.size) {
                    val parentBoundingVolume = parentModelCacheComponent.meshSpatials[it].boundingVolume
                    StaticTransformSpatial(
                        transform,
                        AABB(parentBoundingVolume.localMin, parentBoundingVolume.localMax)
                    )
                }
            }
        }
    }

    override fun onLinkKilled(sourceId: Int, targetId: Int) {
        instancesComponentMapper.getOrNull(targetId)?.instances?.remove(sourceId)
    }

    override fun onTargetDead(sourceId: Int, deadTargetId: Int) {
        instancesComponentMapper.getOrNull(deadTargetId)?.instances?.remove(sourceId)
    }

    override fun onTargetChanged(sourceId: Int, targetId: Int, oldTargetId: Int) {
        instancesComponentMapper[oldTargetId].instances.remove(sourceId)
        onLinkEstablished(sourceId, targetId)
    }
}