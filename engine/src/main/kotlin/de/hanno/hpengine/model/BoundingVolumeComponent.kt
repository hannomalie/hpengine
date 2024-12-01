package de.hanno.hpengine.model

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.transform.AABB
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.koin.core.annotation.Singleton

class BoundingVolumeComponent: Component() {
    val boundingVolume = AABB()
}

@Singleton(binds = [BaseSystem::class])
@All(value = [TransformComponent::class, BoundingVolumeComponent::class])
class BoundingVolumeComponentSystem: BaseEntitySystem() {
    private val logger = LogManager.getLogger(BoundingVolumeComponentSystem::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>

    private val transformCache = mutableMapOf<Int, Matrix4f>() // TODO: Use caching?

    override fun processSystem() {
        forEachEntity {
            modelCacheComponentMapper.getOrNull(it)?.let { modelCacheComponent ->
                boundingVolumeComponentMapper[it].boundingVolume.apply {
                    localMin.set(modelCacheComponent.model.boundingVolume.min)
                    localMax.set(modelCacheComponent.model.boundingVolume.max)
                }
            }

            val boundingVolume = boundingVolumeComponentMapper[it].boundingVolume
            val transform = transformComponentMapper[it].transform
            boundingVolume.recalculate(transform)
        }
    }

}