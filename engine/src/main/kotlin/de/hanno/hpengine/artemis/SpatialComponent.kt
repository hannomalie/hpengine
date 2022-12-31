package de.hanno.hpengine.artemis

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.transform.*
import org.joml.Vector3f

class SpatialComponent: Component() {
    lateinit var spatial: SimpleSpatial
}

@All(
    SpatialComponent::class,
    TransformComponent::class,
    BoundingVolumeComponent::class
)
class SpatialComponentSystem: BaseEntitySystem() {
    lateinit var spatialComponentMapper: ComponentMapper<SpatialComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>

    override fun inserted(entityId: Int) {
        super.inserted(entityId)
        spatialComponentMapper[entityId].spatial = TransformSpatial(
            transformComponentMapper[entityId].transform,
            boundingVolumeComponentMapper[entityId].boundingVolume
        )
    }
    override fun processSystem() { }
}


fun List<SpatialComponent>.calculateAABB(): AABBData {
    val minResult = Vector3f(absoluteMaximum)
    val maxResult = Vector3f(absoluteMinimum)
    forEach {
        minResult.min(it.spatial.boundingVolume.min)
        maxResult.max(it.spatial.boundingVolume.max)
    }
    return AABBData(minResult, maxResult)
}
