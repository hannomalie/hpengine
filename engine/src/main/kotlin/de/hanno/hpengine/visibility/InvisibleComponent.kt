package de.hanno.hpengine.visibility

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import de.hanno.hpengine.model.material.MaterialManager
import org.koin.core.annotation.Single

class InvisibleComponent: Component()

@Single(binds=[BaseSystem::class, InvisibleComponentSystem::class])
class InvisibleComponentSystem: BaseSystem() {
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>

    override fun processSystem() { }

}