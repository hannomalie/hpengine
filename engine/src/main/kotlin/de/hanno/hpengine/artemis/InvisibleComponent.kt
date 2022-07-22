package de.hanno.hpengine.artemis

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper

class InvisibleComponent: Component()

class InvisibleComponentSystem: BaseSystem() {
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>

    override fun processSystem() { }

}