package de.hanno.hpengine.graphics.renderer.rendertarget


import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32

class FrameBuffer(override val frameBuffer: Int, val depthBuffer: DepthBuffer<*>?) : IFrameBuffer {

    companion object {
        context(GpuContext)
        operator fun invoke(depthBuffer: DepthBuffer<*>?): FrameBuffer {
            return FrameBuffer(onGpu { glGenFramebuffers() }, depthBuffer).apply {
                if (depthBuffer != null) {
                    onGpu {
                        bindFrameBuffer(this)
                        GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.texture.id, 0)
                    }
                }
            }
        }

        val FrontBuffer = FrameBuffer(0, null)
    }
}