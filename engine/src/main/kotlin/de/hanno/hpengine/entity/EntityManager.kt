package de.hanno.hpengine.entity

import com.artemis.BaseEntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.system.Extractor

@All
class CycleSystem(
    private val entitiesStateHolder: EntitiesStateHolder,
): BaseEntitySystem(), Extractor {
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
        val entitiesState = currentWriteState[entitiesStateHolder.entitiesState]
        entitiesState.entityMovedInCycle = entityMovedInCycle
        entitiesState.staticEntityMovedInCycle = staticEntityMovedInCycle
        entitiesState.entityAddedInCycle = entityAddedInCycle
        entitiesState.componentAddedInCycle = componentAddedInCycle
    }
}
