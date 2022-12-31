package de.hanno.hpengine.artemis

import com.artemis.Component

class NameComponent: Component() {
    var name: String = System.currentTimeMillis().toString()
}