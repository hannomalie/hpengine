package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GpuContext
import org.joml.Vector4f

interface FrontBufferTarget {
    val width: Int
    val height: Int
    val name: String get() = "FrontBufferTarget"
    val clear: Vector4f
    fun use(gpuContext: GpuContext, clear: Boolean)
}