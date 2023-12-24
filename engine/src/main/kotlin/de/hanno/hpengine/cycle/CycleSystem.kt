package de.hanno.hpengine.cycle

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.annotations.All
import org.koin.core.annotation.Single

@All
@Single(binds=[BaseSystem::class, CycleSystem::class])
class CycleSystem: BaseEntitySystem() {
    var cycle = 0L
        private set

    override fun processSystem() {
        cycle += 1
    }
    // TODO: Implement cycle increment after all other systems processing, use in EntityMovementSystem
}
