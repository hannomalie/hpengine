package de.hanno.hpengine.engine.graphics.buffer

import de.hanno.hpengine.engine.graphics.GpuContext
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL44

import java.nio.ByteBuffer

import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT

val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

abstract class AbstractPersistentMappedBuffer @JvmOverloads constructor(private val gpuContext: GpuContext<*>,
                                              protected var target: Int,
                                              capacityInBytes: Int = 1024) : GPUBuffer {
    override var id: Int = 0
    override lateinit var buffer: ByteBuffer
    override var sizeInBytes: Int
        get() = buffer.capacity()
        set(value) {
            ensureCapacityInBytes(value)
        }

    init {
        gpuContext.calculate {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            this.buffer = newBuffer
            this.id = id
        }
    }
    private val bindBufferRunnable = {
        if (id <= 0) {
            id = glGenBuffers()
        }
        glBindBuffer(target, id)
    }

    fun map(requestedCapacityInBytes: Int): ByteBuffer? {
        bind()
        if (requestedCapacityInBytes > buffer.capacity()) {
            ensureCapacityInBytes(requestedCapacityInBytes)
        }

        buffer.clear()
        return buffer
    }

    @Synchronized
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        if (::buffer.isInitialized) {
            val needsResize = buffer.capacity() <= capacityInBytes
            if (needsResize) {
                val (id, newBuffer) = gpuContext.calculate {
                    delete()
                    unbind()
                    createBuffer(capacityInBytes)
                }
                copyOldBufferTo(newBuffer)
                this.buffer = newBuffer
                this.id = id
            }
        } else {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            copyOldBufferTo(newBuffer)
            this.buffer = newBuffer
            this.id = id
        }
    }

    private fun copyOldBufferTo(byteBuffer: ByteBuffer) {
        if (::buffer.isInitialized) {
            val array = ByteArray(buffer.capacity())
            buffer.rewind()
            buffer.get(array)
//            byteBuffer.put(buffer)
            byteBuffer.put(array)
            byteBuffer.rewind()
        }
    }

    private fun delete() = gpuContext.calculate {
        bind()
        if (glGetBufferParameteri(target, GL_BUFFER_MAPPED) == GL11.GL_TRUE) {
            glUnmapBuffer(target)
        }
        if (id > 0) {
            glDeleteBuffers(id)
            id = -1
        }
    }

    private fun createBuffer(capacityInBytes: Int): Pair<Int, ByteBuffer> = gpuContext.calculate {
        val id = glGenBuffers()
        glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong(), flags)
        Pair(id, newBuffer)
    }

    open fun mapBuffer(capacityInBytes: Long, flags: Int): ByteBuffer {
        //            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt());
        val byteBuffer = GL30.glMapBufferRange(target,
                0, capacityInBytes, flags,
//                null)!!
                xxxx)!!
        copyOldBufferTo(byteBuffer)
        return byteBuffer
    }

    override fun bind() {
        gpuContext.execute("AbstractPersistentMappedBuffer.bind", bindBufferRunnable)
    }

    override fun unbind() {
        gpuContext.execute("AbstractPersistentMappedBuffer.unbind") { glBindBuffer(target, 0) }
    }
}
