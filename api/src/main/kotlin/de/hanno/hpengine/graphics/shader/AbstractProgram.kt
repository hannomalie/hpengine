package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.createFileListeners
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

abstract class AbstractProgram<T : Uniforms>(
    override val shaders: List<Shader>,
    val defines: Defines = Defines(),
    override val uniforms: T,
    private val gpuContext: GpuContext, // TODO: Make context receiver, currently triggers compiler bugs
    private val fileMonitor: FileMonitor,
) : IProgram<T> {

    override val id = gpuContext.createProgramId()
    val name: String = shaders.joinToString(",") { it.name }

    private var longBuffer: LongBuffer? = null
    val fileListeners: MutableList<FileAlterationListenerAdaptor> = ArrayList()

    override val uniformBindings = HashMap<String, UniformBinding>()

    init {
//        TODO: Reimplement
        fileMonitor.run {
            gpuContext.run {
                load()
                createFileListeners()
            }
        }
    }
    protected fun clearUniforms() = gpuContext.run {
        uniformBindings.clear()
    }

    override fun setUniform(name: String, value: Int) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Boolean) = gpuContext.run {
        val valueAsInt = if (value) 1 else 0
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(valueAsInt)
    }

    override fun setUniform(name: String, value: Float) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Long) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, longs: LongArray) = gpuContext.run {
        if (longBuffer == null) {
            val newLongBuffer = BufferUtils.createLongBuffer(longs.size)
            longBuffer = newLongBuffer
            newLongBuffer.rewind()
            newLongBuffer.put(longs)
        }
        setUniform(name, longBuffer)
    }

    override fun setUniform(name: String, buffer: LongBuffer?) = gpuContext.run {
        buffer!!.rewind()
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(buffer)
    }

    override fun setUniform(name: String, value: Double) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value.toFloat())
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniform(name: String, x: Float, y: Float, z: Float) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(x, y, z)
    }

    override fun setUniform(name: String, vec: Vector3f) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector3fc) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector2f) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y)
    }

    override fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    override fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer) = gpuContext.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    private fun createBindingIfMissing(name: String) = gpuContext.run {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getUniformLocation(name))
        }
    }
    private fun createShaderStorageBindingIfMissing(name: String) = gpuContext.run {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getShaderStorageBlockIndex(name))
        }
    }
}