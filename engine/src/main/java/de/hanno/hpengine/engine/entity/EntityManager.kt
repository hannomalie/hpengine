package de.hanno.hpengine.engine.entity

import com.artemis.BaseEntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.system.Extractor

@All
class CycleSystem: BaseEntitySystem(), Extractor {
    var cycle = 0L
    override fun processSystem() {
        cycle += 1
    }
    // TODO: Implement this
    var entityHasMoved = true
    var staticEntityHasMoved = true
    val entityMovedInCycle: Long get() = cycle
    val staticEntityMovedInCycle: Long get() = cycle
    private var entityAddedInCycle = 0L
    private var componentAddedInCycle = 0L

    override fun inserted(entityId: Int) {
        entityAddedInCycle = cycle
        componentAddedInCycle = cycle
    }
    override fun extract(currentWriteState: RenderState) {
        currentWriteState.entitiesState.entityMovedInCycle = entityMovedInCycle
        currentWriteState.entitiesState.staticEntityMovedInCycle = staticEntityMovedInCycle
        currentWriteState.entitiesState.entityAddedInCycle = entityAddedInCycle
        currentWriteState.entitiesState.componentAddedInCycle = componentAddedInCycle
    }
}
