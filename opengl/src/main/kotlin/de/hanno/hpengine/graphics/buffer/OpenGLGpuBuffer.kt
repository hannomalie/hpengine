package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.glValue
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData
import java.nio.ByteBuffer

context(GraphicsApi)
class OpenGLGpuBuffer(
    override var target: BufferTarget,
    capacityInBytes: Int = 1024
) : GpuBuffer {

    private var bufferDefinition: BufferDefinition = createBuffer(capacityInBytes)
        set(value) {
            val oldBufferDefinition = bufferDefinition
            oldBufferDefinition.copyTo(value)
            field = value
            onGpu { glDeleteBuffers(oldBufferDefinition.id) }
        }

    override val id: Int get() = bufferDefinition.id
    override val buffer: ByteBuffer get() = bufferDefinition.buffer

    init {
        unmap()
    }

    @Synchronized // TODO: remove this
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        val needsResize = buffer.capacity() < capacityInBytes
        if (needsResize) {
            bufferDefinition = createBuffer(capacityInBytes)
        }
    }

    private fun BufferDefinition.copyTo(newBuffer: BufferDefinition) = onGpu {
        glCopyNamedBufferSubData(this.id, newBuffer.id, 0, 0, this.buffer.capacity().toLong())
    }

    private fun createBuffer(capacityInBytes: Int): BufferDefinition = onGpu {
        val id = glGenBuffers()
        glBindBuffer(target.glValue, id)
        GL44.glBufferStorage(target.glValue, capacityInBytes.toLong(), flags)
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes)

        val byteBuffer = GL30.glMapBufferRange(
            target.glValue,
            0, capacityInBytes.toLong(), flags,
            xxxx
        )!!

        BufferDefinition(id, byteBuffer)
    }
    override fun bind() = onGpu {
        glBindBuffer(target.glValue, id)
    }

    override fun unbind() = onGpu { glBindBuffer(target.glValue, 0) }
    override fun delete() = onGpu {
        glDeleteBuffers(id)
    }

    override fun map(): Unit = onGpu {
        bind()
        GL30.glMapBufferRange(
            target.glValue,
            0, bufferDefinition.buffer.capacity().toLong(), flags,
            bufferDefinition.buffer
        )!!
    }

    override fun unmap(): Unit = onGpu {
        bind()
        GL30.glUnmapBuffer(target.glValue)
    }

    private companion object {
        const val flags = GL_MAP_WRITE_BIT
    }
}
