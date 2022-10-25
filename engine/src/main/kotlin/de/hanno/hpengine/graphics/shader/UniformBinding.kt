package de.hanno.hpengine.graphics.shader

import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL20
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

fun UniformBinding.set(value: Int) {
    if (location != -1) {
        GL20.glUniform1i(location, value)
    }
}

fun UniformBinding.set(value: Float) {
    if (location != -1) {
        GL20.glUniform1f(location, value)
    }
}

fun UniformBinding.set(value: Long) {
    if (location != -1) {
        ARBBindlessTexture.glUniformHandleui64ARB(location, value)
    }
}

operator fun UniformBinding.set(x: Float, y: Float, z: Float) {
    if (location != -1) {
        GL20.glUniform3f(location, x, y, z)
    }
}

operator fun UniformBinding.set(x: Float, y: Float) {
    if (location != -1) {
        GL20.glUniform2f(location, x, y)
    }
}

fun UniformBinding.setAsMatrix4(values: FloatBuffer) {
    if (location != -1) {
        GL20.glUniformMatrix4fv(location, false, values)
    }
}

fun UniformBinding.setAsMatrix4(values: ByteBuffer) {
    if (location != -1) {
        GL20.glUniformMatrix4fv(location, false, values.asFloatBuffer())
    }
}

fun UniformBinding.set(values: LongBuffer) {
    if (location != -1) {
        ARBBindlessTexture.glUniformHandleui64vARB(location, values)
    }
}

fun UniformBinding.setVec3ArrayAsFloatBuffer(values: FloatBuffer) {
    if (location != -1) {
        GL20.glUniform3fv(location, values)
    }
}

fun UniformBinding.setFloatArrayAsFloatBuffer(values: FloatBuffer) {
    if (location != -1) {
        GL20.glUniform1fv(location, values)
    }
}