package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.struct.Struct
import de.hanno.struct.Structable

class AreaLightXXX(parent: Structable? = null) : Struct(parent) {
    val trafo by Matrix4f(this)
    val color by Vector3f(this)
    var dummy0 by 0
    val widthHeightRange by Vector3f(this)
    var dummy1 by 0
}