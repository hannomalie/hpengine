package de.hanno.hpengine.graphics.light.point

import de.hanno.hpengine.math.Vector3f
import de.hanno.struct.Struct

class PointLightStruct : Struct() {
    val position by Vector3f()
    var radius by 0.0f
    val color by Vector3f()
    val dummy by 0

    companion object {
        fun getBytesPerInstance() = java.lang.Double.BYTES * 8
    }
}