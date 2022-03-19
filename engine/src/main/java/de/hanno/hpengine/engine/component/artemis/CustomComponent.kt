package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.systems.IteratingSystem

class CustomComponent(var update: (Float) -> Unit = {}): Component()

@All(CustomComponent::class)
class CustomComponentSystem: IteratingSystem() {
    lateinit var customComponentMapper: ComponentMapper<CustomComponent>

    override fun process(entityId: Int) {
        customComponentMapper[entityId].update(world.delta)
    }
}