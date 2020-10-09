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

abstract class AbstractProgram<T: Uniforms>(val id: Int, val defines: Defines = Defines(), val uniformsXXX: T): Reloadable {
    abstract var shaders: List<Shader>
        protected set
    val fileListeners: MutableList<OnFileChangeListener> = ArrayList()
    protected var uniforms = HashMap<String, Uniform?>()

    fun registerUniforms() {
        uniformsXXX.registeredUniforms.forEach {
            putInMapIfAbsent(it.name)
            it.onRegister(this)
        }
    }

    open fun use() {
        GL20.glUseProgram(id)
    }
    protected fun clearUniforms() {
        uniforms.clear()
    }

    fun setUniform(name: String, value: Int) {
        putInMapIfAbsent(name)
        uniforms[name]!!.set(value)
    }

    fun setUniform(name: String, value: Boolean) {
        val valueAsInt = if (value) 1 else 0
        putInMapIfAbsent(name)
        uniforms[name]!!.set(valueAsInt)
    }

    fun setUniform(name: String, value: Float) {
        putInMapIfAbsent(name)
        uniforms[name]!!.set(value)
    }

    fun setUniform(name: String, value: Long) {
        putInMapIfAbsent(name)
        uniforms[name]!!.set(value)
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
        putInMapIfAbsent(name)
        uniforms[name]!!.set(buffer)
    }

    fun setUniform(name: String, value: Double) {
        putInMapIfAbsent(name)
        uniforms[name]!!.set(value.toFloat())
    }

    fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) {
        putInMapIfAbsent(name)
        uniforms[name]!!.setAsMatrix4(matrixBuffer)
    }

    fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) {
        putInMapIfAbsent(name)
        uniforms[name]!!.setAsMatrix4(matrixBuffer)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float) {
        putInMapIfAbsent(name)
        uniforms[name]!![x, y] = z
    }

    fun setUniform(name: String, vec: Vector3f) {
        putInMapIfAbsent(name)
        uniforms[name]!![vec.x, vec.y] = vec.z
    }
    fun setUniform(name: String, vec: Vector3fc) {
        putInMapIfAbsent(name)
        uniforms[name]!![vec.x, vec.y] = vec.z
    }

    fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer?) {
        putInMapIfAbsent(name)
        uniforms[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer?) {
        putInMapIfAbsent(name)
        uniforms[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    fun setUniformAsBlock(name: String, fs: FloatArray?) {
        putBlockInMapIfAbsent(name)
        try {
            (uniforms[name] as UniformBlock?)!!.set(fs)
        } catch (e: ClassCastException) {
            System.err.println("You can't set a non block uniform as block!")
            e.printStackTrace()
        }
    }

    protected fun putInMapIfAbsent(name: String) {
        if (!uniforms.containsKey(name)) {
            uniforms[name] = Uniform(this, name)
        }
    }

    private fun putBlockInMapIfAbsent(name: String) {
        if (!uniforms.containsKey(name)) {
            uniforms[name] = UniformBlock(this, name)
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

    fun getUniform(key: String?): Uniform? {
        return uniforms[key]
    }

    fun addEmptyUniform(uniform: Uniform) {
        uniforms[uniform.name] = uniform
    }

    fun UniformDelegate<*>.bind() = when (this) {
        is Mat4 -> GL20.glUniformMatrix4fv(location, false, _value)
        is Vec3 -> GL20.glUniform3f(location, _value.x, _value.y, _value.z)
        is SSBO<*> -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, _value.id)
        is IntType -> GL20.glUniform1i(location, _value)
        is BooleanType -> GL20.glUniform1i(location, if(_value) 1 else 0)
    }

    fun bind() = uniformsXXX.registeredUniforms.forEach {
        it.bind()
    }
}

inline fun <T: Uniforms> AbstractProgram<T>.useAndBind(block: (T) -> Unit) {
    use()
    block(uniformsXXX)
    bind()
}
fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}