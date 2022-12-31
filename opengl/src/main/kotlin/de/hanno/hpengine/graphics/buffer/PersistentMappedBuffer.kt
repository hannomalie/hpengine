package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.constants.BufferTarget
import de.hanno.hpengine.graphics.renderer.constants.glValue
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData
import java.nio.ByteBuffer

val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

context(GraphicsApi)
class PersistentMappedBuffer(
    override var target: BufferTarget,
    capacityInBytes: Int = 1024
) : GpuBuffer {
    private var bufferDefinition: BufferDefinition = createBuffer(capacityInBytes)
        set(value) {
            val oldBufferDefinition = bufferDefinition
            oldBufferDefinition.copyTo(value)
            field = value
            oldBufferDefinition.deleteBuffer()
        }

    override val id: Int get() = bufferDefinition.id
    override val buffer: ByteBuffer get() = bufferDefinition.buffer

    @Synchronized // TODO: remove this
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        val needsResize = buffer.capacity() < capacityInBytes
        if (needsResize) {
            val newBufferDefinition = onGpu {
                createBuffer(capacityInBytes)
            }
            bufferDefinition = newBufferDefinition
        }
    }

    private fun BufferDefinition.copyTo(newBuffer: BufferDefinition) {
        onGpu {
            glCopyNamedBufferSubData(this.id, newBuffer.id, 0, 0, this.buffer.capacity().toLong())
        }
    }

    private fun createBuffer(capacityInBytes: Int): BufferDefinition = onGpu {
        val id = glGenBuffers()
        glBindBuffer(target.glValue, id)
        GL44.glBufferStorage(target.glValue, capacityInBytes.toLong(), flags)
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes)
        val byteBuffer = GL30.glMapBufferRange(
            target.glValue,
            0, capacityInBytes.toLong(), flags,
//                null)!!
            xxxx
        )!!

        BufferDefinition(id, byteBuffer)
    }
    override fun bind() = onGpu {
        glBindBuffer(target.glValue, id)
    }

    private fun BufferDefinition.deleteBuffer() = onGpu {
        glDeleteBuffers(id)
    }

    override fun unbind() = onGpu { glBindBuffer(target.glValue, 0) }
    override fun delete() {
        GL15.glDeleteBuffers(id)
    }
}

private data class BufferDefinition(val id: Int, val buffer: ByteBuffer) {
    init {
        require(id > 0) { "Buffer id is invalid: $id" }
    }
}