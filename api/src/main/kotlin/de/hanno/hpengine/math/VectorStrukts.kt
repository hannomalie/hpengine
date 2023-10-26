package de.hanno.hpengine.math

import de.hanno.hpengine.Transform
import de.hanno.hpengine.transform.w
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import org.joml.Vector4fc
import org.joml.Vector4ic
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import java.nio.ByteBuffer

annotation class Order(val value: Int)

interface Vector2fStrukt : Strukt {
    context(ByteBuffer) @Order(0) var x: Float
    context(ByteBuffer) @Order(1) var y: Float

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
    context(ByteBuffer) @Order(0) var x: Float
    context(ByteBuffer) @Order(1) var y: Float
    context(ByteBuffer) @Order(2) var z: Float

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
    context(ByteBuffer) @Order(0) var x: Float
    context(ByteBuffer) @Order(1) var y: Float
    context(ByteBuffer) @Order(2) var z: Float
    context(ByteBuffer) @Order(3) var w: Float

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
    context(ByteBuffer) @Order(0) var x: Int
    context(ByteBuffer) @Order(1) var y: Int
    context(ByteBuffer) @Order(2) var z: Int
    context(ByteBuffer) @Order(3) var w: Int

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
    context(ByteBuffer) @Order(0) var m00: Float
    context(ByteBuffer) @Order(1) var m01: Float
    context(ByteBuffer) @Order(2) var m02: Float
    context(ByteBuffer) @Order(3) var m03: Float
    context(ByteBuffer) @Order(4) var m10: Float
    context(ByteBuffer) @Order(5) var m11: Float
    context(ByteBuffer) @Order(6) var m12: Float
    context(ByteBuffer) @Order(7) var m13: Float
    context(ByteBuffer) @Order(8) var m20: Float
    context(ByteBuffer) @Order(9) var m21: Float
    context(ByteBuffer) @Order(10) var m22: Float
    context(ByteBuffer) @Order(11) var m23: Float
    context(ByteBuffer) @Order(12) var m30: Float
    context(ByteBuffer) @Order(13) var m31: Float
    context(ByteBuffer) @Order(14) var m32: Float
    context(ByteBuffer) @Order(15) var m33: Float

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

    companion object
}

val identityMatrix4fBuffer = BufferUtils.createFloatBuffer(16).apply {
    Transform().get(this)
}