package de.hanno.hpengine.graphics.renderer.rendertarget

interface FrameBuffer {
    val frameBuffer: Int
    val depthBuffer: DepthBuffer<*>?
}