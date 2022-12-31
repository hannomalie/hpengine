package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import java.nio.IntBuffer

context(GraphicsApi)
fun OpenGLIndexBuffer(): IndexBuffer {
    val underlying = PersistentMappedBuffer(10 * Int.SIZE_BYTES, BufferTarget.ElementArray)
    return object: IndexBuffer, GpuBuffer by underlying {}
}

context(GraphicsApi)
fun OpenGLIndexBuffer(intBuffer: IntBuffer): IndexBuffer = OpenGLIndexBuffer().apply {
    ensureCapacityInBytes(intBuffer.capacity())
    buffer.asIntBuffer().put(intBuffer)
}
