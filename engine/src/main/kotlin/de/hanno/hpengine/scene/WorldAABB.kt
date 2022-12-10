package de.hanno.hpengine.scene

import com.artemis.BaseEntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.SpatialComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import org.joml.Vector3f

@All(TransformComponent::class, SpatialComponent::class)
class WorldAABB(
    private val worldAABBStateHolder: WorldAABBStateHolder,
): BaseEntitySystem(), Extractor {
    val aabb = AABB(Vector3f(100f)) // TODO: Find sensible default?

    override fun inserted(entityId: Int) {
//        TODO: Implement
//        aabb.localAABB = calculateAABB()
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[worldAABBStateHolder.worldAABBState].sceneMin.set(aabb.min)
        currentWriteState[worldAABBStateHolder.worldAABBState].sceneMax.set(aabb.max)
    }
    override fun processSystem() { }
}

context(GpuContext, RenderStateContext)
class WorldAABBStateHolder {
    val worldAABBState = renderState.registerState {
        MinMax(sceneMin = Vector3f(), sceneMax = Vector3f())
    }
}
data class MinMax(val sceneMin: Vector3f, val sceneMax: Vector3f)
