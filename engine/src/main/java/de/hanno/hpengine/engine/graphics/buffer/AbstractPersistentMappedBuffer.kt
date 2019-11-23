package de.hanno.hpengine.engine.graphics.buffer

import de.hanno.hpengine.engine.graphics.GpuContext
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL44

import java.nio.ByteBuffer

import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData

val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

abstract class AbstractPersistentMappedBuffer @JvmOverloads constructor(private val gpuContext: GpuContext<*>,
                                              protected var target: Int,
                                              capacityInBytes: Int = 1024) : GPUBuffer {
    private var bufferDefinition: BufferDefinition = createBuffer(capacityInBytes)
        set(value) {
            val oldBufferDefinition = bufferDefinition
            oldBufferDefinition.copyTo(value)
            field = value
            oldBufferDefinition.deleteBuffer()
        }

    override val id: Int
        get() = bufferDefinition.id
    override val buffer: ByteBuffer
        get() = bufferDefinition.buffer

    override var sizeInBytes: Int
        get() = buffer.capacity()
        set(value) {
            ensureCapacityInBytes(value)
        }

    private val bindBufferRunnable = {
        glBindBuffer(target, id)
    }

    @Synchronized
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        val needsResize = buffer.capacity() < capacityInBytes
        if (needsResize) {
            val newBufferDefinition = gpuContext.calculate {
                createBuffer(capacityInBytes)
            }
            bufferDefinition = newBufferDefinition
        }
    }

    private fun BufferDefinition.copyTo(newBuffer: BufferDefinition) {
        gpuContext.execute("copy buffers") {
            glCopyNamedBufferSubData(this.id, newBuffer.id, 0, 0, this.buffer.capacity().toLong())
        }
    }

    data class BufferDefinition(val id: Int, val buffer: ByteBuffer, val gpuContext: GpuContext<*>) {
        init {
            require(id > 0) { "Buffer id is invalid: $id" }
        }

        fun deleteBuffer() = gpuContext.execute("delete buffers") {
            glDeleteBuffers(id)
        }
    }
    private fun createBuffer(capacityInBytes: Int): BufferDefinition = gpuContext.calculate {
        val id = glGenBuffers()
        glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes)
        val byteBuffer = GL30.glMapBufferRange(target,
                0, capacityInBytes.toLong(), flags,
//                null)!!
                xxxx)!!
        BufferDefinition(id, byteBuffer, gpuContext)
    }

    override fun bind() {
        gpuContext.execute("AbstractPersistentMappedBuffer.bind", bindBufferRunnable)
    }

    override fun unbind() {
        gpuContext.execute("AbstractPersistentMappedBuffer.unbind") { glBindBuffer(target, 0) }
    }
}
