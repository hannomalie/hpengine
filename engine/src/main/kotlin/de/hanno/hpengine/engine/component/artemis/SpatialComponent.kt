package de.hanno.hpengine.engine.component.artemis

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.TransformSpatial

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