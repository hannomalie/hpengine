package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.toCount
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL15

fun GraphicsApi.AtomicCounterBuffer(size: ElementCount = 1.toCount()): AtomicCounterBuffer {
    val underlying = PersistentMappedBuffer(
        BufferTarget.ElementArray,
        SizeInBytes(size, SizeInBytes(Integer.BYTES)),
    )
    return object : AtomicCounterBuffer, GpuBuffer by underlying {
        override fun bindAsParameterBuffer() {
            GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id)
        }
    }
}