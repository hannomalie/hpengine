package de.hanno.hpengine.engine.graphics.shader

import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL20
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

data class UniformBinding(val name: String, val location: Int) {
    fun set(value: Int) {
        if (location != -1) {
            GL20.glUniform1i(location, value)
        }
    }

    fun set(value: Float) {
        if (location != -1) {
            GL20.glUniform1f(location, value)
        }
    }

    fun set(value: Long) {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64ARB(location, value)
        }
    }

    operator fun set(x: Float, y: Float, z: Float) {
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z)
        }
    }

    fun setAsMatrix4(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values)
        }
    }

    fun setAsMatrix4(values: ByteBuffer) {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values.asFloatBuffer())
        }
    }

    fun set(values: LongBuffer) {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64vARB(location, values)
        }
    }

    fun setVec3ArrayAsFloatBuffer(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniform3fv(location, values)
        }
    }

    fun setFloatArrayAsFloatBuffer(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniform1fv(location, values)
        }
    }

}