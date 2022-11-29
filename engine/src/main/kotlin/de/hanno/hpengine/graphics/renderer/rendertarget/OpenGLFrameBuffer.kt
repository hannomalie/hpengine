package de.hanno.hpengine.graphics.renderer.rendertarget


import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32

class OpenGLFrameBuffer(override val frameBuffer: Int, val depthBuffer: DepthBuffer<*>?) : FrameBuffer {

    companion object {
        context(GpuContext)
        operator fun invoke(depthBuffer: DepthBuffer<*>?) = OpenGLFrameBuffer(
            frameBuffer = onGpu { glGenFramebuffers() },
            depthBuffer = depthBuffer
        ).apply {
            if (depthBuffer != null) {
                onGpu {
                    bindFrameBuffer(this)
                    GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.texture.id, 0)
                }
            }
        }

        val FrontBuffer = OpenGLFrameBuffer(0, null)
    }
}