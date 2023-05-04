package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBufferImpl
import de.hanno.hpengine.graphics.constants.BufferTarget
import org.lwjgl.opengl.*
import struktgen.api.*
import java.nio.ByteBuffer

context(GraphicsApi)
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


context(GraphicsApi)
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

context(GraphicsApi)
fun OpenGLIndexBuffer(size: Int = 1000) = PersistentMappedBuffer(
    BufferTarget.ElementArray, size * IntStrukt.sizeInBytes
).typed(IntStrukt.type)

class PersistentTypedBuffer<T: Strukt>(
    override val typedBuffer: TypedBuffer<T>,
    override val gpuBuffer: PersistentMappedBuffer
): TypedGpuBuffer<T>, ITypedBuffer<T> by typedBuffer, GpuBuffer by gpuBuffer

fun <T: Strukt> GpuBuffer.typed(struktType: StruktType<T>): TypedGpuBuffer<T> = TypedGpuBufferImpl(
    this, struktType
)
