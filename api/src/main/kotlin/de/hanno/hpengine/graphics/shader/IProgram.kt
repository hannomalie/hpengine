package de.hanno.hpengine.graphics.shader

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

interface IComputeProgram<T: Uniforms>: IProgram<T> {
    fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int)
}

interface IProgram<T: Uniforms> {
    val id: Int
    val uniforms: T
    val shaders: List<Shader>
    fun setUniform(name: String, value: Int)
    fun setUniform(name: String, value: Boolean)
    fun setUniform(name: String, value: Float)
    fun setUniform(name: String, value: Long)
    fun setUniform(name: String, longs: LongArray)
    fun setUniform(name: String, buffer: LongBuffer?)
    fun setUniform(name: String, value: Double)
    fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer)
    fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer)
    fun setUniform(name: String, x: Float, y: Float, z: Float)
    fun setUniform(name: String, vec: Vector3f)
    fun setUniform(name: String, vec: Vector3fc)
    fun setUniform(name: String, vec: Vector2f)
    fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer)
    fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer)
    val uniformBindings: HashMap<String, UniformBinding>
}

val Map.Entry<String, Any>.defineText: String
    get() = when (value) {
        is Boolean -> "const bool $key = $value;\n"
        is Int -> "const int $key = $value;\n"
        is Float -> "const float $key = $value;\n"
        else -> throw java.lang.IllegalStateException("Local define not supported type for $key - $value")
    }

fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}