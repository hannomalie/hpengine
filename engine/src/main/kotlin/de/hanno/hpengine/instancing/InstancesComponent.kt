package de.hanno.hpengine.instancing

import com.artemis.Component

class InstancesComponent: Component() {
    val instances = mutableListOf<Int>()
}