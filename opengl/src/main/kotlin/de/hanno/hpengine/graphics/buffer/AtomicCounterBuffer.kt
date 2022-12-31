package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL15

context(GraphicsApi)
fun AtomicCounterBuffer(size: Int = 1): AtomicCounterBuffer {
    val underlying = PersistentMappedBuffer(
        BufferTarget.ElementArray,
        size * Integer.BYTES,
    )
    return object : AtomicCounterBuffer, GpuBuffer by underlying {
        override fun bindAsParameterBuffer() {
            GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id)
        }
    }
}