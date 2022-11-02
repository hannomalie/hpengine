package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.renderer.pipelines.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.shader.api.Shader
import de.hanno.hpengine.graphics.shader.define.Defines
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

interface IComputeProgram<T: Uniforms>: IProgram<T> {
    fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int)
}

interface IProgram<T: Uniforms> {
    val uniforms: T
    val shaders: List<Shader>
    fun use()
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
    fun getUniformLocation(name: String): Int
    fun bindShaderStorageBuffer(index: Int, block: GpuBuffer)
    fun bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer)
    fun getShaderStorageBlockIndex(name: String): Int
    fun getShaderStorageBlockBinding(name: String, bindingIndex: Int)
    fun UniformDelegate<*>.bind()
    fun bind()
}

abstract class AbstractProgram<T : Uniforms>(
    val id: Int,
    val defines: Defines = Defines(),
    override val uniforms: T
) : IProgram<T>, Reloadable {
    abstract override var shaders: List<Shader>
        protected set

    val fileListeners: MutableList<OnFileChangeListener> = ArrayList()

    protected val uniformBindings = HashMap<String, UniformBinding>()

    protected fun clearUniforms() {
        uniformBindings.clear()
    }

    override fun bind() = uniforms.registeredUniforms.forEach {
        it.bind()
    }
}

fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}