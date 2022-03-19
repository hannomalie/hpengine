package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import de.hanno.hpengine.engine.transform.AABB

class BoundingVolumeComponent: Component() {
    lateinit var boundingVolume: AABB
}