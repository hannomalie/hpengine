package de.hanno.hpengine.instancing

import com.artemis.Component
import com.artemis.annotations.EntityId

class InstanceComponent: Component() {
    @JvmField
    @field:EntityId
    var targetEntity = -1
}