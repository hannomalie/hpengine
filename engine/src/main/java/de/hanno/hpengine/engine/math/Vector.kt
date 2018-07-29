package de.hanno.hpengine.engine.math

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.struct.Struct

class Vector3f(parent: Struct? = null) : Struct(parent) {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f
}
class Matrix4f(parent: Struct? = null): Struct(parent) {
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

    fun set(joint: BufferableMatrix4f) {
        this.m00 = joint.m00()
        this.m01 = joint.m01()
        this.m02 = joint.m02()
        this.m03 = joint.m03()
        this.m10 = joint.m10()
        this.m11 = joint.m11()
        this.m12 = joint.m12()
        this.m13 = joint.m13()
        this.m20 = joint.m20()
        this.m21 = joint.m21()
        this.m22 = joint.m22()
        this.m23 = joint.m23()
        this.m30 = joint.m30()
        this.m31 = joint.m31()
        this.m32 = joint.m32()
        this.m33 = joint.m33()
    }
}
class AABB(parent: Struct? = null): Struct(parent) {
    val min by Vector3f(this)
    val max by Vector3f(this)
}