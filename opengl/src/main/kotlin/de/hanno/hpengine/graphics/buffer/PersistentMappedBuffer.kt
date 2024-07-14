package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.glValue
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData
import java.nio.ByteBuffer

class PersistentMappedBuffer(
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

    @Synchronized // TODO: remove this
    override fun ensureCapacityInBytes(requestedCapacity: SizeInBytes) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= SizeInBytes(0)) {
            capacityInBytes = SizeInBytes(10)
        }

        val needsResize = SizeInBytes(buffer.capacity()) < capacityInBytes
        if (needsResize) {
            val newBufferDefinition = graphicsApi.onGpu {
                createBuffer(capacityInBytes)
            }
            bufferDefinition = newBufferDefinition
        }
    }

    override fun put(src: ByteBuffer) {
        buffer.rewind()
        buffer.put(src)
        buffer.rewind()
    }
    private fun BufferDefinition.copyTo(newBuffer: BufferDefinition) {
        graphicsApi.onGpu {
            glCopyNamedBufferSubData(this.id, newBuffer.id, 0, 0, this.buffer.capacity().toLong())
        }
    }

    private fun createBuffer(capacityInBytes: SizeInBytes): BufferDefinition = graphicsApi.onGpu {
        val id = glGenBuffers()
        glBindBuffer(target.glValue, id)
        GL44.glBufferStorage(target.glValue, capacityInBytes.value, flags)
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.value.toInt())
        val byteBuffer = GL30.glMapBufferRange(
            target.glValue,
            0, capacityInBytes.value, flags,
//                null)!!
            xxxx
        )!!

        BufferDefinition(id, byteBuffer)
    }
    override fun bind() = graphicsApi.onGpu {
        glBindBuffer(target.glValue, id)
    }

    override fun unbind() = graphicsApi.onGpu { glBindBuffer(target.glValue, 0) }
    override fun delete() = graphicsApi.onGpu {
        glDeleteBuffers(id)
    }

    private companion object {
        const val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT
    }
}
