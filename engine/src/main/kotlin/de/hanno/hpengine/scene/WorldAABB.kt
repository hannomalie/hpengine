package de.hanno.hpengine.scene

import com.artemis.BaseEntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.SpatialComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.RenderStateContext
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
        currentWriteState[worldAABBStateHolder.worldAABBState].min.set(aabb.min)
        currentWriteState[worldAABBStateHolder.worldAABBState].max.set(aabb.max)
    }
    override fun processSystem() { }
}

context(de.hanno.hpengine.graphics.GraphicsApi, RenderStateContext)
class WorldAABBStateHolder {
    val worldAABBState = renderState.registerState {
        MinMax(min = Vector3f(), max = Vector3f())
    }
}
