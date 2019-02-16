package de.hanno.hpengine.engine.math

import de.hanno.struct.Struct
import org.joml.Matrix4f
import org.joml.Vector4f

class Vector3f(parent: Struct? = null) : Struct(parent) {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f

    fun set(target: org.joml.Vector3f) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }

    fun set(target: Vector4f) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }
}

class Vector4f(parent: Struct? = null) : Struct(parent) {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f
    var w by 0.0f

    fun set(target: org.joml.Vector4f) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = target.w
    }
}
class Vector4i(parent: Struct? = null) : Struct(parent) {
    var x by 0
    var y by 0
    var z by 0
    var w by 0

    fun set(target: org.joml.Vector4i) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = target.w
    }
}
class Vector2f(parent: Struct? = null) : Struct(parent) {
    var x by 0.0f
    var y by 0.0f

    fun set(target: org.joml.Vector2f) {
        this.x = target.x
        this.y = target.y
    }
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

    fun <T: Matrix4f> set(source: T) {
        source.get(baseByteOffset.toInt(), buffer)
//        this.m00 = source.m00()
//        this.m01 = source.m01()
//        this.m02 = source.m02()
//        this.m03 = source.m03()
//        this.m10 = source.m10()
//        this.m11 = source.m11()
//        this.m12 = source.m12()
//        this.m13 = source.m13()
//        this.m20 = source.m20()
//        this.m21 = source.m21()
//        this.m22 = source.m22()
//        this.m23 = source.m23()
//        this.m30 = source.m30()
//        this.m31 = source.m31()
//        this.m32 = source.m32()
//        this.m33 = source.m33()
    }
}
class AABB(parent: Struct? = null): Struct(parent) {
    val min by Vector3f(this)
    val max by Vector3f(this)
}