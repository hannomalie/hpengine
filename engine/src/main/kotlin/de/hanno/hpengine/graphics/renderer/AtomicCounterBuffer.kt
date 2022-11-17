package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.AbstractPersistentMappedBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43

context(GpuContext)
class AtomicCounterBuffer(size: Int) : AbstractPersistentMappedBuffer(GL43.GL_SHADER_STORAGE_BUFFER) {
    init {
        ensureCapacityInBytes(size * Integer.BYTES)
    }

    fun bindAsParameterBuffer() {
        GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id)
    }
}