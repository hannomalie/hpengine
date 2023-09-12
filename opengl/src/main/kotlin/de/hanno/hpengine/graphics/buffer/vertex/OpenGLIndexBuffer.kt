package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import java.nio.IntBuffer

fun OpenGLIndexBuffer(
    graphicsApi: GraphicsApi,
): IndexBuffer {
    val underlying = graphicsApi.PersistentMappedBuffer(BufferTarget.ElementArray, 10 * Int.SIZE_BYTES)
    return object: IndexBuffer, GpuBuffer by underlying {}
}

fun OpenGLIndexBuffer(
    graphicsApi: GraphicsApi,
    intBuffer: IntBuffer
): IndexBuffer = OpenGLIndexBuffer(graphicsApi).apply {
    ensureCapacityInBytes(intBuffer.capacity())
    buffer.asIntBuffer().put(intBuffer)
}
