package de.hanno.hpengine.engine.math

import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension.Companion.identityMatrix44Buffer
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.w
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import de.hanno.struct.Struct
import org.joml.Matrix4f
import org.joml.Vector4f
import org.joml.Vector4fc
import org.lwjgl.BufferUtils

class Vector3f : Struct() {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f

    fun set(target: org.joml.Vector3fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }

    fun set(target: Vector4fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }

    fun toJoml(): org.joml.Vector3f = org.joml.Vector3f(x, y, z)
    override fun toString() = "($x, $y, $z)"
}

fun org.joml.Vector3f.toHp() = Vector3f().apply {
    x = x()
    y = y()
    z = z()
}

class Vector4f : Struct() {
    var x by 0.0f
    var y by 0.0f
    var z by 0.0f
    var w by 0.0f

    fun set(target: org.joml.Vector4fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = target.w
    }
    fun set(target: org.joml.Vector3fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = 1.0f
    }
    fun set(target: org.joml.Vector2fc) {
        this.x = target.x()
        this.y = target.y()
    }

    override fun toString() = "($x, $y, $z, $w)"
}
class Vector4i : Struct() {
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
    override fun toString() = "($x, $y, $z, $w)"
}
class Vector2f : Struct() {
    var x by 0.0f
    var y by 0.0f

    fun set(target: org.joml.Vector2f) {
        this.x = target.x
        this.y = target.y
    }
    override fun toString() = "($x, $y)"
}
class Matrix4f : Struct() {
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
    }
}
class AABB : Struct() {
    val min by Vector3f()
    val max by Vector3f()
}

val identityMatrix4fBuffer = BufferUtils.createFloatBuffer(16).apply {
    Transform().get(this)
}