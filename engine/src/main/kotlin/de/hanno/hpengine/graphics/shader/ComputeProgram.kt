package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.renderer.pipelines.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import org.joml.Vector2f
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

class ComputeProgram(
    programManager: OpenGlProgramManager,
    val computeShader: ComputeShader,
    defines: Defines = Defines()
) : AbstractProgram<Uniforms>(programManager.gpuContext.createProgramId(), defines, Uniforms.Empty), IComputeProgram<Uniforms> {

    constructor(programManager: OpenGlProgramManager,
                computeShaderSource: FileBasedCodeSource,
                defines: Defines = Defines()
    ): this(programManager, ComputeShader(programManager, computeShaderSource, defines), defines)

    private val gpuContext = programManager.gpuContext
    override var shaders: List<de.hanno.hpengine.graphics.shader.api.Shader> = listOf(computeShader)

    var longBuffer: LongBuffer? = null

    init {
        load()
        createFileListeners()
    }

    override fun load() {
        shaders.forEach { attach(it) }

        linkProgram()
        validateProgram()

        registerUniforms()
        gpuContext.exceptionOnError()

        createFileListeners()
    }

    // TODO: Extract all those things to an abstractopenglprogram or to programmanager

    override fun use() {
        GL20.glUseProgram(id)
    }

    override fun getUniformLocation(name: String): Int {
        return GL20.glGetUniformLocation(id, name)
    }

    override fun bindShaderStorageBuffer(index: Int, block: GpuBuffer) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, block.id)
    }

    override fun bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer) {
        GL30.glBindBufferBase(GL42.GL_ATOMIC_COUNTER_BUFFER, index, block.id)
    }

    override fun getShaderStorageBlockIndex(name: String): Int {
        return GL43.glGetProgramResourceIndex(id, GL43.GL_SHADER_STORAGE_BLOCK, name)
    }

    override fun getShaderStorageBlockBinding(name: String, bindingIndex: Int) {
        GL43.glShaderStorageBlockBinding(id, getShaderStorageBlockIndex(name), bindingIndex)
    }

    override fun UniformDelegate<*>.bind() = when (this) {
        is Mat4 -> GL20.glUniformMatrix4fv(uniformBindings[name]!!.location, false, _value)
        is Vec3 -> GL20.glUniform3f(uniformBindings[name]!!.location, _value.x, _value.y, _value.z)
        is SSBO -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, uniformBindings[name]!!.location, _value.id)
        is IntType -> GL20.glUniform1i(uniformBindings[name]!!.location, _value)
        is BooleanType -> GL20.glUniform1i(uniformBindings[name]!!.location, if(_value) 1 else 0)
        is FloatType -> GL20.glUniform1f(uniformBindings[name]!!.location, _value)
    }
    override fun setUniform(name: String, value: Int) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Boolean) {
        val valueAsInt = if (value) 1 else 0
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(valueAsInt)
    }

    override fun setUniform(name: String, value: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Long) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, longs: LongArray) {
        if (longBuffer == null) {
            val newLongBuffer = BufferUtils.createLongBuffer(longs.size)
            longBuffer = newLongBuffer
            newLongBuffer.rewind()
            newLongBuffer.put(longs)
        }
        setUniform(name, longBuffer)
    }

    override fun setUniform(name: String, buffer: LongBuffer?) {
        buffer!!.rewind()
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(buffer)
    }

    override fun setUniform(name: String, value: Double) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value.toFloat())
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniform(name: String, x: Float, y: Float, z: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(x, y, z)
    }

    override fun setUniform(name: String, vec: Vector3f) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector3fc) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector2f) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y)
    }

    override fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    override fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    private fun createBindingIfMissing(name: String) {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getUniformLocation(name))
        }
    }
    fun registerUniforms() {
        uniformBindings.clear()
        uniforms.registeredUniforms.forEach {
            uniformBindings[it.name] = when(it) {
                is SSBO -> UniformBinding(it.name, it.bindingIndex)
                is BooleanType -> UniformBinding(it.name, getUniformLocation(it.name))
                is FloatType -> UniformBinding(it.name, getUniformLocation(it.name))
                is IntType -> UniformBinding(it.name, getUniformLocation(it.name))
                is Mat4 -> UniformBinding(it.name, getUniformLocation(it.name))
                is Vec3 -> UniformBinding(it.name, getUniformLocation(it.name))
            }
        }
    }

    private fun attach(shader: de.hanno.hpengine.graphics.shader.api.Shader) {
        GL20.glAttachShader(id, shader.id)
    }

    private fun detach(shader: de.hanno.hpengine.graphics.shader.api.Shader) {
        GL20.glDetachShader(id, shader.id)
    }

    override fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        GL43.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
    }

    override fun unload() {
        GL20.glUseProgram(0)
        GL20.glDeleteProgram(id)
    }

    override fun reload() = try {
        gpuContext.invoke {
            detach(computeShader)
            computeShader.reload()
            load()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    override val name: String = computeShader.name

    override fun equals(other: Any?): Boolean {
        if (other !is ComputeProgram) {
            return false
        }

        return this.computeShader == other.computeShader && this.defines == other.defines
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += computeShader.hashCode()
        hash += defines.hashCode()
        return hash
    }
}
