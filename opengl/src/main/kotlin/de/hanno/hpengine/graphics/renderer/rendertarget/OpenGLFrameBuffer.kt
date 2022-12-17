package de.hanno.hpengine.graphics.renderer.rendertarget


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.CubeMapArray
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.Texture3D
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

                    when(val depthTexture = depthBuffer.texture) {
                        is CubeMap -> {/* TODO: Check whether it works without framebufferrenderbuffer */}
                        is CubeMapArray -> {/* TODO: Check whether it works without framebufferrenderbuffer */}
                        is Texture2D ->
                            run {
                                // TODO: this should not be necessary, but it is, or else no depth buffer is used, investigate!
                                val depthrenderbuffer = glGenRenderbuffers()
                                glBindRenderbuffer(GL_RENDERBUFFER, depthrenderbuffer)
                                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, depthTexture.dimension.width, depthTexture.dimension.height)
                                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthrenderbuffer)
                            }
                        is Texture3D -> {/* TODO: Check whether it works without framebufferrenderbuffer */}
                    }

                    GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.texture.id, 0)
                }
            }
        }

        val FrontBuffer = OpenGLFrameBuffer(0, null)
    }
}