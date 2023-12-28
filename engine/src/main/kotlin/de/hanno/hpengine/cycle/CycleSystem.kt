package de.hanno.hpengine.cycle

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.annotations.All
import de.hanno.hpengine.system.PrioritySystem
import org.koin.core.annotation.Single

@All
@Single(binds=[BaseSystem::class, CycleSystem::class])
class CycleSystem: BaseEntitySystem(), PrioritySystem {
    var cycle = 0L
        private set

    override fun processSystem() {
        cycle += 1
    }

    // This should always be the last system to be processed, so that all other systems see the first cycle as 0
    override val priority: Int = -Int.MAX_VALUE
}
