package de.hanno.hpengine.component

import com.artemis.Component

class NameComponent: Component() {
    var name: String = System.currentTimeMillis().toString()
}