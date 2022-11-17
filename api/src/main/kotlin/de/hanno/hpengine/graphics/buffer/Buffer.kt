package de.hanno.hpengine.graphics.renderer.pipelines

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

interface AtomicCounterBuffer: GpuBuffer