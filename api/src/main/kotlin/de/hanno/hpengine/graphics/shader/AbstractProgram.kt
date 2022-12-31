package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi
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
    private val graphicsApi: GraphicsApi, // TODO: Make context receiver, currently triggers compiler bugs
    private val fileMonitor: FileMonitor,
) : IProgram<T> {

    override val id = graphicsApi.createProgramId()
    val name: String = shaders.joinToString(",") { it.name }

    private var longBuffer: LongBuffer? = null
    val fileListeners: MutableList<FileAlterationListenerAdaptor> = ArrayList()

    override val uniformBindings = HashMap<String, UniformBinding>()

    init {
//        TODO: Reimplement
        fileMonitor.run {
            graphicsApi.run {
                load()
                createFileListeners()
            }
        }
    }
    protected fun clearUniforms() = graphicsApi.run {
        uniformBindings.clear()
    }

    override fun setUniform(name: String, value: Int) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Boolean) = graphicsApi.run {
        val valueAsInt = if (value) 1 else 0
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(valueAsInt)
    }

    override fun setUniform(name: String, value: Float) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Long) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, longs: LongArray) = graphicsApi.run {
        if (longBuffer == null) {
            val newLongBuffer = BufferUtils.createLongBuffer(longs.size)
            longBuffer = newLongBuffer
            newLongBuffer.rewind()
            newLongBuffer.put(longs)
        }
        setUniform(name, longBuffer)
    }

    override fun setUniform(name: String, buffer: LongBuffer?) = graphicsApi.run {
        buffer!!.rewind()
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(buffer)
    }

    override fun setUniform(name: String, value: Double) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value.toFloat())
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniform(name: String, x: Float, y: Float, z: Float) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(x, y, z)
    }

    override fun setUniform(name: String, vec: Vector3f) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector3fc) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector2f) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y)
    }

    override fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    override fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer) = graphicsApi.run {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    private fun createBindingIfMissing(name: String) = graphicsApi.run {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getUniformLocation(name))
        }
    }
    private fun createShaderStorageBindingIfMissing(name: String) = graphicsApi.run {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getShaderStorageBlockIndex(name))
        }
    }
}