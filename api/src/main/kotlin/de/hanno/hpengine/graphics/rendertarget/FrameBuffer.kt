package de.hanno.hpengine.graphics.rendertarget

interface FrameBuffer {
    val frameBuffer: Int
    val depthBuffer: DepthBuffer<*>?
}