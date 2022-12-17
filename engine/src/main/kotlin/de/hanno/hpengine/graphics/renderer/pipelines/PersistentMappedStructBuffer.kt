package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.constants.BufferTarget
import de.hanno.hpengine.math.Vector4fStrukt
import org.lwjgl.opengl.*
import struktgen.api.*
import java.nio.ByteBuffer

context(GpuContext)
class PersistentMappedBufferAllocator(
    val target: BufferTarget = BufferTarget.ShaderStorage
) : Allocator<GpuBuffer> {

    override fun allocate(capacityInBytes: Int, current: GpuBuffer?): GpuBuffer = window.invoke {
        require(capacityInBytes > 0) { "Cannot allocate buffer of size 0!" }

        PersistentShaderStorageBuffer(capacityInBytes).apply {
            current?.buffer?.copyTo(buffer)
        }
    }

    fun ensureCapacityInBytes(oldBuffer: GpuBuffer, requestedCapacity: Int): GpuBuffer {
        val requestedCapacity = if (requestedCapacity <= 0) requestedCapacity else 10 // TODO: This is not very intuitive

        val needsResize = oldBuffer.buffer.capacity() < requestedCapacity
        return if (needsResize) {
            allocate(requestedCapacity, oldBuffer).apply {
                oldBuffer.delete()
            }
        } else oldBuffer
    }
}


context(GpuContext)
fun CommandBuffer(
    size: Int = 1000
) = PersistentShaderStorageBuffer(
    size * DrawElementsIndirectCommandStrukt.type.sizeInBytes
).typed(
    DrawElementsIndirectCommandStrukt.type
)

interface IntStrukt : Strukt {
    context(ByteBuffer) var value: Int

    companion object
}

context(GpuContext)
fun OpenGLIndexBuffer(size: Int = 1000) = PersistentMappedBuffer(
    size * IntStrukt.sizeInBytes, BufferTarget.ElementArray
).typed(IntStrukt.type)

class PersistentTypedBuffer<T: Strukt>(
    override val typedBuffer: TypedBuffer<T>,
    override val gpuBuffer: PersistentMappedBuffer
): TypedGpuBuffer<T>, ITypedBuffer<T> by typedBuffer, GpuBuffer by gpuBuffer

fun <T: Strukt> GpuBuffer.typed(struktType: StruktType<T>): TypedGpuBuffer<T> = TypedGpuBufferImpl(
    this, struktType
)
