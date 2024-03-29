package de.hanno.hpengine.graphics.rendertarget

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

    fun use(graphicsApi: GraphicsApi, clear: Boolean)
}