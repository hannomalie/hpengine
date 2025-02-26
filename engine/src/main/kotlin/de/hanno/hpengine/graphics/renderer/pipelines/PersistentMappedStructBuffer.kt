package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBufferImpl
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.renderer.DrawElementsIndirectCommandStrukt
import de.hanno.hpengine.toCount
import org.jetbrains.kotlin.org.jline.terminal.Size
import struktgen.api.*
import java.nio.ByteBuffer

class PersistentMappedBufferAllocator(
    val graphicsApi: GraphicsApi,
    val target: BufferTarget = BufferTarget.ShaderStorage
) : Allocator<GpuBuffer> {

    override fun allocate(capacityInBytes: SizeInBytes, current: GpuBuffer?): GpuBuffer = graphicsApi.onGpu {
        require(capacityInBytes > SizeInBytes(0)) { "Cannot allocate buffer of size 0!" }

        PersistentShaderStorageBuffer(capacityInBytes).apply {
            current?.buffer?.copyTo(buffer)
        }
    }

    fun ensureCapacityInBytes(oldBuffer: GpuBuffer, requestedCapacity: SizeInBytes): GpuBuffer {
        val requestedCapacity = if (requestedCapacity <= SizeInBytes(0)) requestedCapacity else SizeInBytes(10) // TODO: This is not very intuitive

        val needsResize = SizeInBytes(oldBuffer.buffer.capacity()) < requestedCapacity
        return if (needsResize) {
            allocate(requestedCapacity, oldBuffer).apply {
                oldBuffer.delete()
            }
        } else oldBuffer
    }
}


fun CommandBuffer(
    graphicsApi: GraphicsApi,
    size: ElementCount = 1000.toCount()
) = graphicsApi.PersistentShaderStorageBuffer(
    size * SizeInBytes(DrawElementsIndirectCommandStrukt.type.sizeInBytes)
).typed(
    DrawElementsIndirectCommandStrukt.type
)

interface IntStrukt : Strukt {
    context(ByteBuffer) var value: Int

    companion object
}

fun IndexBuffer(graphicsApi: GraphicsApi, size: ElementCount = 100000.toCount()) = graphicsApi.PersistentMappedBuffer(
    BufferTarget.ElementArray, size * SizeInBytes(IntStrukt.sizeInBytes)
).typed(IntStrukt.type)

class PersistentTypedBuffer<T: Strukt>(
    override val typedBuffer: TypedBuffer<T>,
    override val gpuBuffer: PersistentMappedBuffer
): TypedGpuBuffer<T>, ITypedBuffer<T> by typedBuffer, GpuBuffer by gpuBuffer

fun <T: Strukt> GpuBuffer.typed(struktType: StruktType<T>): TypedGpuBuffer<T> = TypedGpuBufferImpl(
    this, struktType
)
