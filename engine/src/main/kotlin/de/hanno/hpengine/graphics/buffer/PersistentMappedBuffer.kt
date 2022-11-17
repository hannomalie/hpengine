package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL43

context(GpuContext)
fun PersistentMappedBuffer(
    capacityInBytes: Int,
    target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) = AbstractPersistentMappedBuffer(target, capacityInBytes)
