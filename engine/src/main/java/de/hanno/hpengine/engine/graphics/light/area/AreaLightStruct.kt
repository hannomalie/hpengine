package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.math.Vector3f
import de.hanno.struct.Struct

class AreaLightStruct : Struct() {
    val trafo by Matrix4f()
    val color by Vector3f()
    var dummy0 by 0
    val widthHeightRange by Vector3f()
    var dummy1 by 0
}