package de.hanno.hpengine.graphics.rendertarget


import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.CubeMapArray
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.Texture3D
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32

class OpenGLFrameBuffer private constructor(override val frameBuffer: Int, override val depthBuffer: DepthBuffer<*>?) : FrameBuffer {

    companion object {
        operator fun invoke(
            graphicsApi: GraphicsApi,
            depthBuffer: DepthBuffer<*>?
        ) = OpenGLFrameBuffer(
            frameBuffer = graphicsApi.onGpu { glGenFramebuffers() },
            depthBuffer = depthBuffer
        ).apply {
            graphicsApi.onGpu {
                bindFrameBuffer(this)
                val renderBuffer = glGenRenderbuffers()
                glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)

                if (depthBuffer != null) {
                    when(val depthTexture = depthBuffer.texture) {
                        is CubeMap -> {/* TODO: Check whether it works without framebufferrenderbuffer */}
                        is CubeMapArray -> {/* TODO: Check whether it works without framebufferrenderbuffer */}
                        is Texture2D ->
                            run {
                                // TODO: this should not be necessary, but it is, or else no depth buffer is used, investigate!
                                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, depthTexture.dimension.width, depthTexture.dimension.height)
                                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderBuffer)
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