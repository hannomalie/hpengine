package de.hanno.hpengine.artemis

import com.artemis.Component
import de.hanno.hpengine.transform.AABB

class BoundingVolumeComponent: Component() {
    lateinit var boundingVolume: AABB
}