package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.toCount
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.sizeInBytes
import java.nio.IntBuffer

fun OpenGLIndexBuffer(
    graphicsApi: GraphicsApi,
): IndexBuffer {
    val underlying = graphicsApi.PersistentMappedBuffer(BufferTarget.ElementArray, 10.toCount() * Int.sizeInBytes)
    return object: IndexBuffer, GpuBuffer by underlying {}
}

fun OpenGLIndexBuffer(
    graphicsApi: GraphicsApi,
    intBuffer: IntBuffer
): IndexBuffer = OpenGLIndexBuffer(graphicsApi).apply {
    ensureCapacityInBytes(SizeInBytes(intBuffer.capacity() * Int.SIZE_BYTES))
    buffer.asIntBuffer().put(intBuffer)
}
