package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.glValue
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL44.GL_BUFFER_IMMUTABLE_STORAGE
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData
import java.nio.ByteBuffer

class OpenGLGpuBuffer(
    private val graphicsApi: GraphicsApi,
    override var target: BufferTarget,
    capacityInBytes: SizeInBytes = SizeInBytes(1024)
) : GpuBuffer {

    private var bufferDefinition: BufferDefinition = createBuffer(capacityInBytes)
        set(value) {
            val oldBufferDefinition = bufferDefinition
            oldBufferDefinition.copyTo(value)
            field = value
            graphicsApi.onGpu { glDeleteBuffers(oldBufferDefinition.id) }
        }

    override val id: Int get() = bufferDefinition.id
    override val buffer: ByteBuffer get() = bufferDefinition.buffer

    init {
        unmap()
    }

    @Synchronized // TODO: remove this
    override fun ensureCapacityInBytes(requestedCapacity: SizeInBytes) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= SizeInBytes(0)) {
            capacityInBytes = SizeInBytes(10)
        }

        val needsResize = SizeInBytes(buffer.capacity()) < capacityInBytes
        if (needsResize) {
            bufferDefinition = createBuffer(capacityInBytes)
        }
    }

    private fun BufferDefinition.copyTo(newBuffer: BufferDefinition) = graphicsApi.onGpu {
        glCopyNamedBufferSubData(this.id, newBuffer.id, 0, 0, this.buffer.capacity().toLong())
    }

    private fun createBuffer(capacityInBytes: SizeInBytes): BufferDefinition = graphicsApi.onGpu {
        val id = glGenBuffers()
        glBindBuffer(target.glValue, id)
        GL44.glBufferStorage(target.glValue, capacityInBytes.value, flags)

        val byteBuffer = GL30.glMapBufferRange(
            target.glValue,
            0, capacityInBytes.value, flags,
            BufferUtils.createByteBuffer(capacityInBytes.value.toInt())
        )!!

        val isImmutable = glGetBufferParameteri(target.glValue, GL_BUFFER_IMMUTABLE_STORAGE) == GL_TRUE
        BufferDefinition(id, byteBuffer, isImmutable)
    }
    override fun bind() = graphicsApi.onGpu {
        glBindBuffer(target.glValue, id)
    }

    override fun unbind() = graphicsApi.onGpu { glBindBuffer(target.glValue, 0) }
    override fun delete() = graphicsApi.onGpu {
        glDeleteBuffers(id)
    }

    override fun map(): Unit = graphicsApi.onGpu {
        bind()
        GL30.glMapBufferRange(
            target.glValue,
            0, bufferDefinition.buffer.capacity().toLong(), flags,
            bufferDefinition.buffer
        )!!
    }

    override fun unmap(): Unit = graphicsApi.onGpu {
        bind()
        GL30.glUnmapBuffer(target.glValue)
    }

    private companion object {
        const val flags = GL_MAP_WRITE_BIT
    }
}
