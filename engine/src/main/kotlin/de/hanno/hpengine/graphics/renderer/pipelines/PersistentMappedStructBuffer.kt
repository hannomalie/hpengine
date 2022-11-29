package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.flags
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import struktgen.api.Strukt
import struktgen.api.StruktType
import struktgen.api.TypedBuffer
import java.nio.ByteBuffer

context(GpuContext)
class PersistentMappedBufferAllocator(
    val target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) : Allocator<GpuBuffer> {

    override fun allocate(capacityInBytes: Int, current: GpuBuffer?): GpuBuffer = window.invoke {
        require(capacityInBytes > 0) { "Cannot allocate buffer of size 0!" }

        val id = GL15.glGenBuffers()
        GL15.glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong(), current)

        object : GpuBuffer {
            override val target = this@PersistentMappedBufferAllocator.target
            override val id = id
            override val buffer = newBuffer
            override fun ensureCapacityInBytes(requestedCapacity: Int) {
                ensureCapacityInBytes(null, capacityInBytes)
            }

            override fun bind() = GL15.glBindBuffer(target, id)
            override fun unbind() = GL15.glBindBuffer(target, 0)
        }
    }

    private fun mapBuffer(capacityInBytes: Long, oldBuffer: GpuBuffer?): ByteBuffer {
        require(capacityInBytes > 0) { "Cannot map buffer with 0 capacity!" }
//            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt())
        val byteBuffer = GL30.glMapBufferRange(
            target,
            0, capacityInBytes, flags,
//                null)!!
            xxxx
        )!!

        oldBuffer?.buffer?.copyTo(byteBuffer)
        return byteBuffer
    }

    fun ensureCapacityInBytes(oldBuffer: GpuBuffer?, requestedCapacity: Int): GpuBuffer {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        return if (oldBuffer != null) {
            val needsResize = oldBuffer.buffer.capacity() < capacityInBytes
            if (needsResize) {
                val newBuffer = allocate(capacityInBytes, oldBuffer)
                onGpu {
                    GL15.glDeleteBuffers(oldBuffer.id)
                }
                newBuffer
            } else oldBuffer
        } else {
            allocate(capacityInBytes, null)
        }
    }
}


context(GpuContext)
fun CommandBuffer(
    size: Int = 1000
) = PersistentMappedBuffer(
    size * DrawElementsIndirectCommandStrukt.type.sizeInBytes
).typed(
    DrawElementsIndirectCommandStrukt.type
)

interface IntStrukt : Strukt {
    context(ByteBuffer) var value: Int

    companion object
}

context(GpuContext)
fun IndexBuffer(size: Int = 1000) = PersistentMappedBuffer(
    size * IntStrukt.sizeInBytes, GL40.GL_ELEMENT_ARRAY_BUFFER
).typed(IntStrukt.type)

data class PersistentTypedBuffer<T: Strukt>(
    val persistentMappedBuffer: PersistentMappedBuffer, val type: StruktType<T>
) : GpuBuffer by persistentMappedBuffer, TypedGpuBuffer<T> {

    override val typedBuffer = object : TypedBuffer<T>(type) {
        override val byteBuffer: ByteBuffer get() = persistentMappedBuffer.buffer
    }
    override val buffer: ByteBuffer get() = persistentMappedBuffer.buffer

    @Synchronized
    override fun ensureCapacityInBytes(requestedCapacity: Int) = persistentMappedBuffer.ensureCapacityInBytes(requestedCapacity)

    @Synchronized
    fun resize(requestedCapacity: Int) = persistentMappedBuffer.resize(requestedCapacity)

    fun addAll(offset: Int? = null, elements: TypedBuffer<T>) = addAll(offset, elements.byteBuffer)
    fun addAll(offset: Int? = null, elements: ByteBuffer) {
        val offset = offset ?: buffer.capacity()
        ensureCapacityInBytes(offset + elements.capacity())
        elements.copyTo(buffer, targetOffsetInBytes = offset)
    }
}

fun <T: Strukt> PersistentMappedBuffer.typed(type: StruktType<T>) = PersistentTypedBuffer(this, type)

context(GpuContext)
class PersistentMappedBuffer(
    initialSizeInBytes: Int,
    _target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) : GpuBuffer {

    private val allocator = PersistentMappedBufferAllocator(_target)

    private var gpuBuffer: GpuBuffer = allocator.allocate(initialSizeInBytes, null)

    override val buffer: ByteBuffer get() = gpuBuffer.buffer
    override val id: Int get() = gpuBuffer.id
    override val target: Int get() = gpuBuffer.target

    @Synchronized
    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        gpuBuffer = allocator.ensureCapacityInBytes(gpuBuffer, requestedCapacity)
    }

    override fun bind() {
        onGpu {
            GL15.glBindBuffer(target, id)
        }
    }

    override fun unbind() {
        onGpu { GL15.glBindBuffer(target, 0) }
    }

    @Synchronized
    fun resize(requestedCapacityInBytes: Int) {
        ensureCapacityInBytes(requestedCapacityInBytes)
    }

    fun enlarge(sizeInBytes: Int, copyContent: Boolean = true) = enlargeToBytes(sizeInBytes, copyContent)

    fun enlargeBy(sizeInBytes: Int, copyContent: Boolean = true) = enlarge(this.sizeInBytes + sizeInBytes, copyContent)

    fun enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) {
        ensureCapacityInBytes(sizeInBytes)
    }
}
