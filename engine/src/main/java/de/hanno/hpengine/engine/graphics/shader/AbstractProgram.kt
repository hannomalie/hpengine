package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import de.hanno.hpengine.util.ressources.OnFileChangeListener
import de.hanno.hpengine.util.ressources.Reloadable
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.ArrayList
import java.util.HashMap

abstract class AbstractProgram<T: Uniforms>(val id: Int, val defines: Defines = Defines(), val uniforms: T): Reloadable {
    abstract var shaders: List<Shader>
        protected set

    val fileListeners: MutableList<OnFileChangeListener> = ArrayList()

    protected val uniformBindings = HashMap<String, UniformBinding>()

    fun registerUniforms() {
        uniformBindings.clear()
        uniforms.registeredUniforms.forEach {
            uniformBindings[it.name] = when(it) {
                is SSBO<*> -> UniformBinding(it.name, it.bindingIndex)
                else -> UniformBinding(it.name, getUniformLocation(it.name))
            }
        }
    }

    fun use() {
        GL20.glUseProgram(id)
    }
    protected fun clearUniforms() {
        uniformBindings.clear()
    }

    fun setUniform(name: String, value: Int) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    fun setUniform(name: String, value: Boolean) {
        val valueAsInt = if (value) 1 else 0
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(valueAsInt)
    }

    fun setUniform(name: String, value: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    fun setUniform(name: String, value: Long) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    var longBuffer: LongBuffer? = null
    fun setUniform(name: String, longs: LongArray) {
        if (longBuffer == null) {
            val newLongBuffer = BufferUtils.createLongBuffer(longs.size)
            longBuffer = newLongBuffer
            newLongBuffer.rewind()
            newLongBuffer.put(longs)
        }
        setUniform(name, longBuffer)
    }

    fun setUniform(name: String, buffer: LongBuffer?) {
        buffer!!.rewind()
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(buffer)
    }

    fun setUniform(name: String, value: Double) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value.toFloat())
    }

    fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!![x, y] = z
    }

    fun setUniform(name: String, vec: Vector3f) {
        createBindingIfMissing(name)
        uniformBindings[name]!![vec.x, vec.y] = vec.z
    }
    fun setUniform(name: String, vec: Vector3fc) {
        createBindingIfMissing(name)
        uniformBindings[name]!![vec.x, vec.y] = vec.z
    }

    fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    private fun createBindingIfMissing(name: String) {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getUniformLocation(name))
        }
    }
    private fun createShaderStorageBindingIfMissing(name: String) {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getShaderStorageBlockIndex(name))
        }
    }

    fun getUniformLocation(name: String): Int {
        return GL20.glGetUniformLocation(id, name)
    }

    fun bindShaderStorageBuffer(index: Int, block: GPUBuffer) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, block.id)
    }

    fun bindShaderStorageBuffer(index: Int, buffer: PersistentMappedStructBuffer<*>) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer.id)
    }

    fun bindAtomicCounterBufferBuffer(index: Int, block: GPUBuffer) {
        GL30.glBindBufferBase(GL42.GL_ATOMIC_COUNTER_BUFFER, index, block.id)
    }

    fun getShaderStorageBlockIndex(name: String): Int {
        return GL43.glGetProgramResourceIndex(id, GL43.GL_SHADER_STORAGE_BLOCK, name)
    }

    fun getShaderStorageBlockBinding(name: String, bindingIndex: Int) {
        GL43.glShaderStorageBlockBinding(id, getShaderStorageBlockIndex(name), bindingIndex)
    }

    fun UniformDelegate<*>.bind() = when (this) {
        is Mat4 -> GL20.glUniformMatrix4fv(uniformBindings[name]!!.location, false, _value)
        is Vec3 -> GL20.glUniform3f(uniformBindings[name]!!.location, _value.x, _value.y, _value.z)
        is SSBO<*> -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, uniformBindings[name]!!.location, _value.id)
        is IntType -> GL20.glUniform1i(uniformBindings[name]!!.location, _value)
        is BooleanType -> GL20.glUniform1i(uniformBindings[name]!!.location, if(_value) 1 else 0)
        is FloatType -> GL20.glUniform1f(uniformBindings[name]!!.location, _value)
    }

    fun bind() = uniforms.registeredUniforms.forEach {
        it.bind()
    }
}

inline fun <T: Uniforms> AbstractProgram<T>.useAndBind(block: (T) -> Unit) {
    use()
    block(uniforms)
    bind()
}
fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}