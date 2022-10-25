package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.renderer.pipelines.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.shader.api.Shader
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import de.hanno.hpengine.ressources.OnFileChangeListener
import de.hanno.hpengine.ressources.Reloadable
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.ArrayList
import java.util.HashMap

abstract class AbstractProgram<T: Uniforms>(val id: Int, val defines: Defines = Defines(), val uniforms: T):
    Reloadable {
    abstract var shaders: List<Shader>
        protected set

    val fileListeners: MutableList<OnFileChangeListener> = ArrayList()

    protected val uniformBindings = HashMap<String, UniformBinding>()

    abstract fun use()

    protected fun clearUniforms() {
        uniformBindings.clear()
    }

    abstract fun setUniform(name: String, value: Int)
    abstract fun setUniform(name: String, value: Boolean)
    abstract fun setUniform(name: String, value: Float)
    abstract fun setUniform(name: String, value: Long)
    abstract fun setUniform(name: String, longs: LongArray)
    abstract fun setUniform(name: String, buffer: LongBuffer?)
    abstract fun setUniform(name: String, value: Double)
    abstract fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer)
    abstract fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer)
    abstract fun setUniform(name: String, x: Float, y: Float, z: Float)
    abstract fun setUniform(name: String, vec: Vector3f)
    abstract fun setUniform(name: String, vec: Vector3fc)
    abstract fun setUniform(name: String, vec: Vector2f)
    abstract fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer)
    abstract fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer)
    abstract fun getUniformLocation(name: String): Int
    abstract fun bindShaderStorageBuffer(index: Int, block: GpuBuffer)
    abstract fun bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer)
    abstract fun getShaderStorageBlockIndex(name: String): Int
    abstract fun getShaderStorageBlockBinding(name: String, bindingIndex: Int)
    abstract fun UniformDelegate<*>.bind()

    fun bind() = uniforms.registeredUniforms.forEach {
        it.bind()
    }
}

fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}