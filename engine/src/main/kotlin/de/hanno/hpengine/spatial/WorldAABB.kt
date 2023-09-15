package de.hanno.hpengine.spatial

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.annotations.All
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.scene.MinMax
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABB
import org.joml.Vector3f
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, WorldAABB::class])
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
        currentWriteState[worldAABBStateHolder.worldAABBState].min.set(aabb.min)
        currentWriteState[worldAABBStateHolder.worldAABBState].max.set(aabb.max)
    }
    override fun processSystem() { }
}

@Single
class WorldAABBStateHolder(
    renderStateContext: RenderStateContext
) {
    val worldAABBState = renderStateContext.renderState.registerState {
        MinMax(min = Vector3f(), max = Vector3f())
    }
}
