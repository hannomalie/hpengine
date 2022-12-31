package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import java.nio.ByteBuffer

fun IndexBuffer.put(offset: Int = 0, values: IntArray) {
    ensureCapacityInBytes((values.size + offset) * Integer.BYTES)
    val intBuffer = buffer.asIntBuffer()
    intBuffer.position(offset)
    intBuffer.put(values)
    buffer.rewind()
}

fun IndexBuffer.put(index: Int = 0, value: Int) {
    buffer.rewind()
    buffer.asIntBuffer().put(index, value)
}

fun IndexBuffer.appendIndices(indexOffset: Int = 0, indices: ByteBuffer) {
    buffer.rewind()
    ensureCapacityInBytes((indexOffset + (indices.capacity() / Integer.BYTES)) * Integer.BYTES)
    indices.copyTo(buffer, indexOffset * Integer.BYTES)
}