package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.flags
import de.hanno.hpengine.buffers.copyTo
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GL44
import struktgen.TypedBuffer
import struktgen.api.Strukt
import struktgen.api.StruktType
import java.nio.ByteBuffer

interface Buffer {
    val buffer: ByteBuffer
}

interface GpuBuffer : Buffer {
    val target: Int
    val id: Int
    override val buffer: ByteBuffer
}

interface TypedGpuBuffer<T> : GpuBuffer {
    val typedBuffer: TypedBuffer<T>
}


interface Allocator<T : Buffer> {
    fun allocate(capacityInBytes: Int) = allocate(capacityInBytes, null)
    fun allocate(capacityInBytes: Int, current: T?): T
}

class PersistentMappedBufferAllocator(
    val gpuContext: GpuContext<*>,
    val target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) : Allocator<GpuBuffer> {

    override fun allocate(capacityInBytes: Int, current: GpuBuffer?): GpuBuffer = gpuContext.window.invoke {
        require(capacityInBytes > 0) { "Cannot allocate buffer of size 0!" }
        val id = GL15.glGenBuffers()
        GL15.glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong(), current)

        object : GpuBuffer {
            override val target = this@PersistentMappedBufferAllocator.target
            override val id = id
            override val buffer = newBuffer
        }
    }

    fun mapBuffer(capacityInBytes: Long, oldBuffer: GpuBuffer?): ByteBuffer {
        require(capacityInBytes > 0) { "Cannot map buffer with 0 capacity!" }
//            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt())
        val byteBuffer = GL30.glMapBufferRange(
            target,
            0, capacityInBytes, flags,
//                null)!!
            xxxx
        )!!

        oldBuffer?.let { oldBuffer ->
            val array = ByteArray(oldBuffer.buffer.capacity())
            oldBuffer.buffer.rewind()
            oldBuffer.buffer.get(array)
//            byteBuffer.put(buffer)
            byteBuffer.put(array)
            byteBuffer.rewind()
        }
        return byteBuffer
    }

    @Synchronized // TODO: Question this
    fun ensureCapacityInBytes(oldBuffer: GpuBuffer?, requestedCapacity: Int): GpuBuffer {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        return if (oldBuffer != null) {
            val needsResize = oldBuffer.buffer.capacity() < capacityInBytes
            if (needsResize) {
                val newBuffer = allocate(capacityInBytes, oldBuffer)
                gpuContext.invoke {
                    GL15.glDeleteBuffers(oldBuffer.id)
                }
                newBuffer
            } else oldBuffer
        } else {
            allocate(capacityInBytes, null)
        }
    }
}


fun CommandBuffer(
    gpuContext: GpuContext<*>,
    size: Int = 1000
) = PersistentMappedBuffer(
    size * DrawElementsIndirectCommandStrukt.type.sizeInBytes,
    gpuContext,
    GL_DRAW_INDIRECT_BUFFER
).typed(
    DrawElementsIndirectCommandStrukt.type
)

interface IntStrukt : Strukt {
    context(ByteBuffer) var value: Int

    companion object
}

fun IndexBuffer(gpuContext: GpuContext<*>, size: Int = 1000) =
    PersistentMappedBuffer(size * IntStrukt.sizeInBytes, gpuContext, GL40.GL_ELEMENT_ARRAY_BUFFER).typed(IntStrukt.type)

data class PersistentTypedBuffer<T>(val persistentMappedBuffer: PersistentMappedBuffer, val type: StruktType<T>) :
    GpuBuffer by persistentMappedBuffer,
    TypedGpuBuffer<T> {
    override val typedBuffer = object : TypedBuffer<T>(type) {
        override val byteBuffer: ByteBuffer get() = persistentMappedBuffer.buffer
    }
    override val buffer: ByteBuffer get() = persistentMappedBuffer.buffer

    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) = persistentMappedBuffer.ensureCapacityInBytes(requestedCapacity)

    @Synchronized
    fun resize(requestedCapacity: Int) = persistentMappedBuffer.resize(requestedCapacity)

    fun addAll(offset: Int? = null, elements: TypedBuffer<T>) = addAll(offset, elements.byteBuffer)
    fun addAll(offset: Int? = null, elements: ByteBuffer) {
        val offset = offset ?: buffer.capacity()
        ensureCapacityInBytes(offset + elements.capacity())
        elements.copyTo(buffer, rewindBuffers = true, targetOffset = offset)
    }
}

fun <T> PersistentMappedBuffer.typed(type: StruktType<T>) = PersistentTypedBuffer(this, type)

class PersistentMappedBuffer(
    initialSizeInBytes: Int,
    private val gpuContext: GpuContext<*>,
    _target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) : GpuBuffer {

    private val allocator = PersistentMappedBufferAllocator(gpuContext, _target)

    private var gpuBuffer: GpuBuffer = allocator.allocate(initialSizeInBytes, null)

    override val buffer: ByteBuffer
        get() = gpuBuffer.buffer
    override val id: Int
        get() = gpuBuffer.id
    override val target: Int
        get() = gpuBuffer.target

    val sizeInBytes: Int
        get() = buffer.capacity()

    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) {
        gpuBuffer = allocator.ensureCapacityInBytes(gpuBuffer, requestedCapacity)
    }

    fun bind() {
        gpuContext.invoke {
            GL15.glBindBuffer(target, id)
        }
    }

    fun unbind() {
        gpuContext.invoke { GL15.glBindBuffer(target, 0) }
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
