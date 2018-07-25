package de.hanno.hpengine.engine.math

import de.hanno.struct.BaseStruct
import de.hanno.struct.Struct

class Vector3f(parent: Struct? = null) : BaseStruct(parent) {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f
}
class Matrix4f constructor(parent: Struct? = null): BaseStruct(parent) {
    var m00 by 0.0f
    var m01 by 0.0f
    var m02 by 0.0f
    var m03 by 0.0f
    var m10 by 0.0f
    var m11 by 0.0f
    var m12 by 0.0f
    var m13 by 0.0f
    var m20 by 0.0f
    var m21 by 0.0f
    var m22 by 0.0f
    var m23 by 0.0f
    var m30 by 0.0f
    var m31 by 0.0f
    var m32 by 0.0f
    var m33 by 0.0f

    override fun toString(): String {
        return """
            |$m00 $m01 $m02 $m03
            |$m10 $m11 $m12 $m13
            |$m20 $m21 $m22 $m23
            |$m30 $m31 $m32 $m33
            """.trimMargin()
    }
}
class AABB(parent: Struct? = null): BaseStruct(parent) {
    val min by Vector3f(this)
    val max by Vector3f(this)
}