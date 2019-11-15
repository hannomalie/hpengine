package de.hanno.hpengine.engine.graphics.buffer

import de.hanno.hpengine.engine.graphics.GpuContext
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL15.glUnmapBuffer
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL43

import java.nio.ByteBuffer

import org.lwjgl.opengl.GL30.glMapBufferRange
import org.lwjgl.opengl.GL44

open class PersistentMappedBuffer @JvmOverloads constructor(val gpuContext: GpuContext<*>,
                                                            capacityInBytes: Int,
                                                            target: Int = GL43.GL_SHADER_STORAGE_BUFFER) : AbstractPersistentMappedBuffer(gpuContext, target) {

    val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

    init {
        gpuContext.calculate {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            this.buffer = newBuffer
            this.id = id
        }
    }

    @Synchronized
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        gpuContext.execute("AbstractPersistentMappedBuffer.setCapacityInBytes0") {
            if (buffer != null) {
                val needsResize = buffer.capacity() <= capacityInBytes
                if (needsResize) {
                    delete()
                    unbind()
                    val (id, newBuffer) = createBuffer(capacityInBytes)
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
    }

    private fun delete() {
        if (GL15.glGetBufferParameteri(target, GL15.GL_BUFFER_MAPPED) == GL11.GL_TRUE) {
            glUnmapBuffer(target)
        }
        if (id > 0) {
            glDeleteBuffers(id)
            id = -1
        }
    }

    private fun createBuffer(capacityInBytes: Int): Pair<Int, ByteBuffer> {
        val id = glGenBuffers()
        glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong(), flags)
        return Pair(id, newBuffer)
    }

    override fun mapBuffer(capacityInBytes: Long, flags: Int): ByteBuffer {
        //            TODO: This causes segfaults in Unsafe class, wtf...
        //        ByteBuffer xxxx = BufferUtils.createByteBuffer((int) capacityInBytes);
        val byteBuffer = glMapBufferRange(target,
                0, capacityInBytes,
                flags, null)!!
        //                xxxx);
        copyOldBufferTo(byteBuffer)
        return byteBuffer
    }

    private fun copyOldBufferTo(byteBuffer: ByteBuffer) {
        if (buffer != null) {
            //            TODO: This causes segfaults in Unsafe class, wtf...
            byteBuffer.put(buffer)
            byteBuffer.rewind()
        }
    }

    override fun putValues(values: ByteBuffer) {
        putValues(0, values)
    }

    override fun putValues(byteOffset: Int, values: ByteBuffer) {
        if (values.capacity() > sizeInBytes) {
            sizeInBytes = values.capacity()
        }
        bind()
        values.rewind()
        buffer.rewind()
        buffer.position(byteOffset)
        buffer.put(values)
        buffer.rewind()
    }

    override fun putValues(vararg values: Float) {
        putValues(0, *values)
    }

    override fun putValues(offset: Int, vararg values: Float) {
        bind()
        buffer.position(offset)
        val doubleValues = DoubleArray(values.size)
        for (i in values.indices) {
            doubleValues[i] = values[i].toDouble()
        }
        buffer.asDoubleBuffer().put(doubleValues)
    }
}
