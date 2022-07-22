package de.hanno.hpengine.graphics.buffer

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL43

open class PersistentMappedBuffer @JvmOverloads constructor(val gpuContext: GpuContext<*>,
                                                            capacityInBytes: Int,
                                                            target: Int = GL43.GL_SHADER_STORAGE_BUFFER) : AbstractPersistentMappedBuffer(gpuContext, target, capacityInBytes) {

}
