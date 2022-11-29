package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL15

context(GpuContext)
fun AtomicCounterBuffer(size: Int = 1): AtomicCounterBuffer {
    val underlying = PersistentMappedBuffer(
        GL15.GL_ELEMENT_ARRAY_BUFFER,
        size * Integer.BYTES,
    )
    return object : AtomicCounterBuffer, GpuBuffer by underlying {
        override fun bindAsParameterBuffer() {
            GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id)
        }
    }
}