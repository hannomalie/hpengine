package de.hanno.hpengine.transform

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.annotations.All
import com.artemis.utils.IntBag
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.system.Extractor
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, EntityAddedSystem::class, Extractor::class])
@All(value = [TransformComponent::class])
class EntityAddedSystem (
    private val entitiesStateHolder: EntitiesStateHolder,
): BaseEntitySystem(), Extractor {
    var cycle = 0L
    var entityAddedInCycle = 0L
    var componentAddedInCycle = 0L

    override fun inserted(entities: IntBag?) {
        entityAddedInCycle = cycle
        componentAddedInCycle = cycle
    }

    override fun processSystem() {
        cycle++
    }

    override fun extract(currentWriteState: RenderState) {
        val entitiesState = currentWriteState[entitiesStateHolder.entitiesState]
        entitiesState.entityAddedInCycle = entityAddedInCycle
        entitiesState.componentAddedInCycle = componentAddedInCycle
    }
}