package de.hanno.hpengine.engine.math

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.w
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
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
    var ByteBuffer.x: Float
    var ByteBuffer.y: Float

    fun ByteBuffer.set(target: org.joml.Vector3fc) {
        this.x = target.x
        this.y = target.y
    }

    fun ByteBuffer.set(target: Vector4fc) {
        this.x = target.x
        this.y = target.y
    }

    fun ByteBuffer.toJoml(): org.joml.Vector2f = org.joml.Vector2f(x, y)
    companion object
}
interface Vector3fStrukt : Strukt {
    var ByteBuffer.x: Float
    var ByteBuffer.y: Float
    var ByteBuffer.z: Float

    fun set(byteBuffer: ByteBuffer, target: org.joml.Vector3fc) = byteBuffer.run {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }

    fun set(byteBuffer: ByteBuffer, target: Vector4fc) = byteBuffer.run {
        this.x = target.x
        this.y = target.y
        this.z = target.z
    }

    fun ByteBuffer.toJoml(): org.joml.Vector3f = org.joml.Vector3f(x, y, z)
    companion object
}

interface Vector4fStrukt : Strukt {
    var ByteBuffer.x: Float
    var ByteBuffer.y: Float
    var ByteBuffer.z: Float
    var ByteBuffer.w: Float

    fun ByteBuffer.set(target: Vector4fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = target.w
    }
    fun ByteBuffer.set(target: org.joml.Vector3fc) {
        this.x = target.x
        this.y = target.y
        this.z = target.z
        this.w = 1.0f
    }
    fun ByteBuffer.set(target: org.joml.Vector2fc) {
        this.x = target.x()
        this.y = target.y()
    }
    companion object
}
interface Vector4iStrukt : Strukt {
    var ByteBuffer.x: Int
    var ByteBuffer.y: Int
    var ByteBuffer.z: Int
    var ByteBuffer.w: Int

    fun ByteBuffer.set(target: Vector4ic) {
        this.x = target.x()
        this.y = target.y()
        this.z = target.z()
        this.w = target.w()
    }
    fun ByteBuffer.set(target: org.joml.Vector3ic) {
        this.x = target.x()
        this.y = target.y()
        this.z = target.z()
        this.w = 1
    }
    fun ByteBuffer.set(target: org.joml.Vector2ic) {
        this.x = target.x()
        this.y = target.y()
    }
    companion object
}
interface Matrix4fStrukt: Strukt {
    var ByteBuffer.m00: Float
    var ByteBuffer.m01: Float
    var ByteBuffer.m02: Float
    var ByteBuffer.m03: Float
    var ByteBuffer.m10: Float
    var ByteBuffer.m11: Float
    var ByteBuffer.m12: Float
    var ByteBuffer.m13: Float
    var ByteBuffer.m20: Float
    var ByteBuffer.m21: Float
    var ByteBuffer.m22: Float
    var ByteBuffer.m23: Float
    var ByteBuffer.m30: Float
    var ByteBuffer.m31: Float
    var ByteBuffer.m32: Float
    var ByteBuffer.m33: Float

    fun set(buffer: ByteBuffer, target: org.joml.Matrix4fc) = buffer.run {
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

    fun set(buffer: ByteBuffer, target: BufferableMatrix4f) = buffer.run {
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