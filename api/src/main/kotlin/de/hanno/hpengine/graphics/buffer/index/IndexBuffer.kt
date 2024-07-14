package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.position
import java.nio.ByteBuffer

fun IndexBuffer.put(offset: ElementCount = ElementCount(0), values: IntArray) {
    ensureCapacityInBytes(SizeInBytes(ElementCount(values.size) + offset, SizeInBytes(Integer.BYTES)))
    val intBuffer = buffer.asIntBuffer()
    intBuffer.position(SizeInBytes(offset, SizeInBytes(Integer.BYTES)))
    intBuffer.put(values)
    buffer.rewind()
}

fun IndexBuffer.put(index: Int = 0, value: Int) {
    buffer.rewind()
    buffer.asIntBuffer().put(index, value)
}

fun IndexBuffer.appendIndices(indexOffset: ElementCount = ElementCount(0), indices: ByteBuffer) {
    buffer.rewind()
    val indicesToAdd = ElementCount(indices.capacity() / Integer.BYTES)
    val requiredSize = SizeInBytes(indexOffset + indicesToAdd, SizeInBytes(Integer.BYTES))
    ensureCapacityInBytes(requiredSize)
    indices.copyTo(buffer, SizeInBytes(indexOffset, SizeInBytes(Integer.BYTES)))
}