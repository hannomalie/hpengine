package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.struct.Struct
import de.hanno.struct.Structable

class PointLightXXX(parent: Structable?) : Struct(parent) {
    val position by Vector3f()
    var radius by 0.0f
    val color by Vector3f()
    val dummy by 0

    companion object {
        fun getBytesPerInstance() = java.lang.Double.BYTES * 8
    }
}