package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import org.joml.Vector4f

interface RenderTarget {
    val width: Int
    val height: Int
    val name: String
    val clear: Vector4f
}

interface FrontBufferTarget: RenderTarget {
    override val name: String get() = "FrontBufferTarget"

    context(GraphicsApi)
    fun use(clear: Boolean)
}