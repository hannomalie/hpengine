package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.buffers.copyTo
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.StruktType
import struktgen.api.TypedBuffer
import java.nio.ByteBuffer

interface Buffer {
    val buffer: ByteBuffer
}

interface GpuBuffer : Buffer {
    val target: Int
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
}
interface IndexBuffer: GpuBuffer

interface AtomicCounterBuffer: GpuBuffer {
    fun bindAsParameterBuffer()
}

interface TypedGpuBuffer<T: Strukt> : GpuBuffer {
    val typedBuffer: TypedBuffer<T>
}

fun <T: Strukt> TypedBuffer<T>.enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(
    size * struktType.sizeInBytes,
    copyContent
)

fun <T: Strukt> TypedBuffer<T>.enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) = if(byteBuffer.capacity() < sizeInBytes) {
    TypedBuffer(BufferUtils.createByteBuffer(sizeInBytes), struktType).apply {
        if(copyContent) {
            this@enlargeToBytes.byteBuffer.copyTo(this@apply.byteBuffer)
        }
    }
} else this