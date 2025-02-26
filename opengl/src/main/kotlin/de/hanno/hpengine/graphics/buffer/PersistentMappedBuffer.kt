package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.glValue
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL44.GL_BUFFER_IMMUTABLE_STORAGE
import org.lwjgl.opengl.GL45.glCopyNamedBufferSubData
import java.nio.ByteBuffer
import kotlin.math.max

private val logger = LogManager.getLogger(PersistentMappedBuffer::class.java)

class PersistentMappedBuffer(
    private val graphicsApi: GraphicsApi,
    override var target: BufferTarget,
    capacityInBytes: SizeInBytes = SizeInBytes(1024)
) : GpuBuffer {
    private var bufferDefinition: BufferDefinition = createBuffer(capacityInBytes)

    override val id: Int get() = bufferDefinition.id
    override val buffer: ByteBuffer get() = bufferDefinition.buffer

    @Synchronized // TODO: remove this
    override fun ensureCapacityInBytes(requestedCapacity: SizeInBytes) {
        val capacityInBytes = SizeInBytes(max(requestedCapacity.value.toInt(), 10))

        val needsResize = SizeInBytes(buffer.capacity()) < capacityInBytes
        if (needsResize) {
            logger.debug("Resizing buffer $id to $capacityInBytes")
            bufferDefinition = createBuffer(capacityInBytes)
            logger.debug("Resized buffer $id")
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
        when(val currentDefinition = bufferDefinition) {
            null -> {
                val id = glGenBuffers()
                glBindBuffer(target.glValue, id)
                GL44.glBufferStorage(target.glValue, capacityInBytes.value, flags)
                val byteBuffer = GL30.glMapBufferRange(
                    target.glValue,
                    0, capacityInBytes.value, flags,
                    BufferUtils.createByteBuffer(max(10, capacityInBytes.value.toInt()))
                )!!
                val isImmutable = glGetBufferParameteri(target.glValue, GL_BUFFER_IMMUTABLE_STORAGE) == GL_TRUE
                BufferDefinition(id, byteBuffer, isImmutable)
            }
            else -> {
                logger.debug("Old buffer ${currentDefinition.id}")
                if(currentDefinition.isImmutable) {
                    val id = glGenBuffers()
                    glBindBuffer(target.glValue, id)
                    GL44.glBufferStorage(target.glValue, capacityInBytes.value, flags)
                    val byteBuffer = GL30.glMapBufferRange(
                        target.glValue,
                        0, capacityInBytes.value, flags,
                        BufferUtils.createByteBuffer(max(10, capacityInBytes.value.toInt()))
                    )!!
                    val isImmutable = glGetBufferParameteri(target.glValue, GL_BUFFER_IMMUTABLE_STORAGE) == GL_TRUE

                    currentDefinition.buffer.copyTo(byteBuffer)
                    glDeleteBuffers(currentDefinition.id)

                    BufferDefinition(id, byteBuffer, isImmutable)
                } else {
                    val byteBuffer = GL30.glMapBufferRange(
                        target.glValue,
                        0, capacityInBytes.value, flags,
                        currentDefinition.buffer
                    )!!
                    currentDefinition.buffer.copyTo(byteBuffer)
                    BufferDefinition(id, byteBuffer, currentDefinition.isImmutable)
                }
            }
        }
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
