package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.renderer.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.texture.OpenGLCubeMapArray
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import org.joml.Vector4f
import org.lwjgl.opengl.GL30
import kotlin.math.max

context(GpuContext)
class RenderTargetImpl<T : Texture>(
    override val frameBuffer: FrameBuffer,
    override val width: Int = 1280,
    override val height: Int = 720,
    override val textures: List<T> = emptyList(),
    override val name: String,
    override val clear: Vector4f = Vector4f(
        0.0f,
        0.0f,
        0.0f,
        0.0f
    )
) : BackBufferRenderTarget<T> {

    override var renderedTextures: IntArray = IntArray(textures.size)
    override var renderedTextureHandles: LongArray = LongArray(textures.size)
    override var drawBuffers: IntArray = IntArray(textures.size)
    override var mipMapCount = calculateMipMapCount(max(width, height))

    // TODO: This is probably not the nicest way to implement this but i am in a hurry :)
    override val factorsForDebugRendering = textures.map { 1f }.toMutableList()

    init {
        onGpu {
            bindFrameBuffer(frameBuffer)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer.frameBuffer)

            if (textures.first() is OpenGLCubeMapArray) {
                textures.forEachIndexed { index, it ->
                    framebufferTextureLayer(index, it, 0, 0)
                }
            } else {
                textures.forEachIndexed { index, it ->
                    framebufferTexture(index, it, 0)
                    drawBuffers[index] = index
                    renderedTextureHandles[index] = it.handle
                    renderedTextures[index] = it.id // TODO: Remove me and the line above me
                }
            }

            drawBuffers(drawBuffers)

            validateFrameBufferState(this@RenderTargetImpl)

            clearColor(clear.x, clear.y, clear.z, clear.w)
        }

        register(this)
    }

    override val renderedTexture: Int
        get() = getRenderedTexture(0)

//    TODO: Reimplement if needed?
//    fun resizeTextures(gpuContext: GpuContext) {
//        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer)
//
//        for (i in colorAttachments.indices) {
//            val (_, internalFormat, textureFilter) = colorAttachments[i]
//
//            val renderedTextureTemp = renderedTextures[i]
//
//            gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp)
//            GL11.glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null as FloatBuffer?)
//
//            GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
//            GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureFilter.minFilter)
//
//            GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
//            GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
//            val borderColorBuffer = BufferUtils.createFloatBuffer(4)
//            val borderColors = floatArrayOf(0f, 0f, 0f, 1f)
//            borderColorBuffer.put(borderColors)
//            borderColorBuffer.rewind()
//            GL11.glTexParameterfv(GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer)
//            GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, renderedTextureTemp, 0)
//        }
//        GL20.glDrawBuffers(drawBuffers)
//    }
//
//    fun resize(gpuContext: GpuContext, width: Int, height: Int) {
//        this.width = width
//        this.height = height
//        resizeTextures(gpuContext)
//    }

    override fun use(clear: Boolean) = onGpu {
        bindFrameBuffer(frameBuffer)
        viewPort(0, 0, width, height)
        if (clear) {
            clearDepthAndColorBuffer()
        }
    }

    override fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int) {
        framebufferTexture(index, textureID, mipMapLevel)
    }

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    override fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        framebufferTexture(
            attachmentIndex,
            index,
            textureId,
            mipmap
        )
    }

    override fun unUse() {
        bindFrameBuffer(0)
    }

    override fun getRenderedTexture(index: Int): Int {
        return renderedTextures[index]
    }

    override fun setRenderedTexture(renderedTexture: Int, index: Int) {
        this.renderedTextures[index] = renderedTexture
    }

    override fun setTargetTextureArrayIndex(textureId: Int, layer: Int) {
        framebufferTextureLayer(0, textureId, 0, layer)
    }
}