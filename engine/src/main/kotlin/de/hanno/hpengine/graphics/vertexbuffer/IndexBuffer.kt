package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.IndexBuffer
import java.nio.IntBuffer
import org.lwjgl.opengl.GL15
import java.nio.ByteBuffer

context(GpuContext)
fun IndexBuffer(): IndexBuffer {
    val underlying = PersistentMappedBuffer(
        GL15.GL_ELEMENT_ARRAY_BUFFER
    )
    return object: IndexBuffer, GpuBuffer by underlying {}
}

context(GpuContext)
fun IndexBuffer(intBuffer: IntBuffer): IndexBuffer = de.hanno.hpengine.graphics.vertexbuffer.IndexBuffer().apply {
    ensureCapacityInBytes(intBuffer.capacity())
    buffer.asIntBuffer().put(intBuffer)
}

fun IndexBuffer.put(values: IntArray) {
    put(0, values)
}

fun IndexBuffer.put(offset: Int, values: IntArray) {
    ensureCapacityInBytes((values.size + offset) * Integer.BYTES)
    val intBuffer = buffer.asIntBuffer()
    intBuffer.position(offset)
    intBuffer.put(values)
    buffer.rewind()
}

/**
 *
 * @param offset
 * @param nonOffsetIndices indices as if no other indices were before in the index buffer
 */
fun IndexBuffer.appendIndices(offset: Int, vararg nonOffsetIndices: Int) {
    buffer.rewind()
    ensureCapacityInBytes((nonOffsetIndices.size + offset) * Integer.BYTES)
    if (offset == 0) {
        put(nonOffsetIndices)
    } else {
        for (i in nonOffsetIndices.indices) {
            put(offset + i, nonOffsetIndices[i])
        }
    }
}

fun IndexBuffer.put(indices: IntBuffer) {
    buffer.rewind()
    indices.rewind()
    buffer.asIntBuffer().put(indices)
}

fun IndexBuffer.put(index: Int, value: Int) {
    buffer.rewind()
    buffer.asIntBuffer().put(index, value)
}

fun IndexBuffer.appendIndices(indexOffset: Int, indices: ByteBuffer) {
    buffer.rewind()
    ensureCapacityInBytes((indexOffset + (indices.capacity() / Integer.BYTES)) * Integer.BYTES)
    indices.copyTo(buffer, indexOffset * Integer.BYTES)
}