package de.hanno.hpengine.math

import de.hanno.hpengine.BufferableMatrix4f
import de.hanno.hpengine.transform.Transform
import de.hanno.hpengine.transform.w
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import de.hanno.struct.Struct
import org.joml.Matrix4f
import org.joml.Vector4fc
import org.joml.Vector4ic
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import java.nio.ByteBuffer

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

    fun set(target: Vector4fc) {
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
interface Vector2fStrukt : Strukt {
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float

    context(ByteBuffer)
    fun set(target: org.joml.Vector3fc) {
        this.x = target.x
        this.y = target.y
    }

    context(ByteBuffer)
    fun set(target: Vector4fc) {
        this.x = target.x
        this.y = target.y
    }

    context(ByteBuffer)
    fun toJoml(): org.joml.Vector2f = org.joml.Vector2f(x, y)
    companion object
}
interface Vector3fStrukt : Strukt {
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float
    context(ByteBuffer) var z: Float

    context(ByteBuffer)
    fun set(target: org.joml.Vector3fc) {
        x = target.x
        y = target.y
        z = target.z
    }

    context(ByteBuffer)
    fun set(target: Vector4fc) {
        x = target.x
        y = target.y
        z = target.z
    }

    context(ByteBuffer)
    fun toJoml(): org.joml.Vector3f = org.joml.Vector3f(x, y, z)
    companion object
}

interface Vector4fStrukt : Strukt {
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float
    context(ByteBuffer) var z: Float
    context(ByteBuffer) var w: Float

    context(ByteBuffer) fun set(target: Vector4fc) {
        x = target.x
        y = target.y
        z = target.z
        w = target.w
    }
    context(ByteBuffer) fun set(target: org.joml.Vector3fc) {
        x = target.x
        y = target.y
        z = target.z
        w = 1.0f
    }
    context(ByteBuffer) fun set(target: org.joml.Vector2fc) {
        x = target.x()
        y = target.y()
    }
    companion object
}
interface Vector4iStrukt : Strukt {
    context(ByteBuffer) var x: Int
    context(ByteBuffer) var y: Int
    context(ByteBuffer) var z: Int
    context(ByteBuffer) var w: Int

    context(ByteBuffer) fun set(target: Vector4ic) {
        x = target.x()
        y = target.y()
        z = target.z()
        w = target.w()
    }
    context(ByteBuffer) fun set(target: org.joml.Vector3ic) {
        x = target.x()
        y = target.y()
        z = target.z()
        w = 1
    }
    context(ByteBuffer) fun set(target: org.joml.Vector2ic) {
        x = target.x()
        y = target.y()
    }
    companion object
}
interface Matrix4fStrukt: Strukt {
    context(ByteBuffer) var m00: Float
    context(ByteBuffer) var m01: Float
    context(ByteBuffer) var m02: Float
    context(ByteBuffer) var m03: Float
    context(ByteBuffer) var m10: Float
    context(ByteBuffer) var m11: Float
    context(ByteBuffer) var m12: Float
    context(ByteBuffer) var m13: Float
    context(ByteBuffer) var m20: Float
    context(ByteBuffer) var m21: Float
    context(ByteBuffer) var m22: Float
    context(ByteBuffer) var m23: Float
    context(ByteBuffer) var m30: Float
    context(ByteBuffer) var m31: Float
    context(ByteBuffer) var m32: Float
    context(ByteBuffer) var m33: Float

    context(ByteBuffer)
    fun set(target: org.joml.Matrix4fc) {
        m00 = target.m00()
        m01 = target.m01()
        m02 = target.m02()
        m03 = target.m03()
        m10 = target.m10()
        m11 = target.m11()
        m12 = target.m12()
        m13 = target.m13()
        m20 = target.m20()
        m21 = target.m21()
        m22 = target.m22()
        m23 = target.m23()
        m30 = target.m30()
        m31 = target.m31()
        m32 = target.m32()
        m33 = target.m33()
    }

    context(ByteBuffer)
    fun set(target: BufferableMatrix4f) {
        m00 = target.m00()
        m01 = target.m01()
        m02 = target.m02()
        m03 = target.m03()
        m10 = target.m10()
        m11 = target.m11()
        m12 = target.m12()
        m13 = target.m13()
        m20 = target.m20()
        m21 = target.m21()
        m22 = target.m22()
        m23 = target.m23()
        m30 = target.m30()
        m31 = target.m31()
        m32 = target.m32()
        m33 = target.m33()
    }
    companion object
}

val identityMatrix4fBuffer = BufferUtils.createFloatBuffer(16).apply {
    Transform().get(this)
}