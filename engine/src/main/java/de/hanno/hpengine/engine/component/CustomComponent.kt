package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.manager.SimpleComponentSystem

interface CustomComponent: Component {

    companion object {
        val identifier = CustomComponent::class.java.simpleName
    }
}

class CustomComponentSystem : SimpleComponentSystem<CustomComponent>(CustomComponent::class.java)
