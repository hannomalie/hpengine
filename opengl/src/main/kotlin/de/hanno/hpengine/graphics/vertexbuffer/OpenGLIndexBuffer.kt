package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.BufferTarget
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.IndexBuffer
import org.lwjgl.opengl.GL15
import java.nio.IntBuffer

context(GpuContext)
fun OpenGLIndexBuffer(): IndexBuffer {
    val underlying = PersistentMappedBuffer(10 * Int.SIZE_BYTES, BufferTarget.ElementArray)
    return object: IndexBuffer, GpuBuffer by underlying {}
}

context(GpuContext)
fun OpenGLIndexBuffer(intBuffer: IntBuffer): IndexBuffer = OpenGLIndexBuffer().apply {
    ensureCapacityInBytes(intBuffer.capacity())
    buffer.asIntBuffer().put(intBuffer)
}
