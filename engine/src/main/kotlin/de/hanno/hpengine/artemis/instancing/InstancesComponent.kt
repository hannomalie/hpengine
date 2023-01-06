package de.hanno.hpengine.artemis.instancing

import com.artemis.Component

class InstancesComponent: Component() {
    val instances = mutableListOf<Int>()
}