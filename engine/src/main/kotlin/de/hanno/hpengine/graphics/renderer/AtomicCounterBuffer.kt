package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.vertexbuffer.IndexBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43

class AtomicCounterBuffer(gpuContext: GpuContext?, size: Int) :
    IndexBuffer(gpuContext, GL43.GL_SHADER_STORAGE_BUFFER) {

    fun bindAsParameterBuffer() {
        GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id)
    }

    init {
        ensureCapacityInBytes(size * Integer.BYTES)
    }
}