package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.buffers.Buffer
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.constants.BufferTarget
import struktgen.api.ITypedBuffer
import struktgen.api.Strukt
import struktgen.api.StruktType
import struktgen.api.TypedBuffer
import java.nio.ByteBuffer

interface GpuBuffer : Buffer {
    val target: BufferTarget
    val id: Int
    override val buffer: ByteBuffer
    var sizeInBytes: Int
        get() = buffer.capacity()
        set(value) {
            ensureCapacityInBytes(value)
        }
    fun ensureCapacityInBytes(requestedCapacity: Int)
    fun bind()
    fun unbind()

    // TODO: I made a change here, check whether buffer.position usage is safe here and makes sense
    fun addAll(offset: Int = buffer.position(), elements: ByteBuffer) {
        ensureCapacityInBytes(offset + elements.capacity())
        elements.copyTo(buffer, targetOffsetInBytes = offset)
    }

    fun delete()
}

interface TypedGpuBuffer<T: Strukt> : GpuBuffer, ITypedBuffer<T> {
    val gpuBuffer: GpuBuffer
    val typedBuffer: TypedBuffer<T>
    // TODO: I made a change here, check whether buffer.position usage is safe here and makes sense
    fun addAll(offset: Int = buffer.position(), elements: TypedBuffer<T>) = addAll(offset, elements.byteBuffer)
}

class TypedGpuBufferImpl<T: Strukt>(
    override val gpuBuffer: GpuBuffer,
    struktType: StruktType<T>,
): TypedGpuBuffer<T>, ITypedBuffer<T>, GpuBuffer {
    override val typedBuffer= object: TypedBuffer<T>(struktType) {
        override val byteBuffer: ByteBuffer get() = gpuBuffer.buffer
    }
    override val struktType: StruktType<T> get() = typedBuffer.struktType
    override val byteBuffer: ByteBuffer get() = typedBuffer.byteBuffer
    override val _slidingWindow: T get() = typedBuffer._slidingWindow
    override val target: BufferTarget get() = gpuBuffer.target
    override val id: Int get() = gpuBuffer.id
    override val buffer: ByteBuffer get() = typedBuffer.byteBuffer

    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        gpuBuffer.ensureCapacityInBytes(requestedCapacity)
    }
    override fun bind() {
        gpuBuffer.bind()
    }
    override fun unbind() {
        gpuBuffer.unbind()
    }
    override fun delete() {
        gpuBuffer.delete()
    }
}

fun <T: Strukt> GpuBuffer.typed(struktType: StruktType<T>): TypedGpuBuffer<T> = TypedGpuBufferImpl(
    this, struktType
)

interface IndexBuffer: GpuBuffer

interface AtomicCounterBuffer: GpuBuffer {
    fun bindAsParameterBuffer()
}
