package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component

class NameComponent: Component() {
    var name: String = System.currentTimeMillis().toString()
}