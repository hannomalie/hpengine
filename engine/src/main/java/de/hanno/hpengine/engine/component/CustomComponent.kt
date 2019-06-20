package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.manager.SimpleComponentSystem

interface CustomComponent: Component {
    override val identifier: String
        get() = Companion.identifier

    companion object {
        val identifier = CustomComponent::class.java.simpleName
    }
}

class CustomComponentSystem : SimpleComponentSystem<CustomComponent>(CustomComponent::class.java, factory = { TODO("Not implemented") })
