package de.hanno.hpengine.engine.graphics.renderer.rendertarget

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.model.texture.UploadState
import de.hanno.hpengine.engine.model.texture.allocateTexture
import de.hanno.hpengine.util.Util
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32
import java.util.logging.Logger

val borderColorBuffer = BufferUtils.createFloatBuffer(4).apply {
    val borderColors = floatArrayOf(0f, 0f, 0f, 1f)
    put(borderColors)
    rewind()
}

open class RenderTarget<T: Texture<*>> @JvmOverloads constructor(val frameBuffer: FrameBuffer,
                                                            open val width: Int = 0,
                                                            open val height: Int = 0,
                                                            val textures: List<T> = emptyList(),
                                                            val name: String,
                                                            val clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)) {

    var renderedTextures: IntArray = IntArray(textures.size)
    var renderedTextureHandles: LongArray = LongArray(textures.size)
    protected var drawBuffers: IntArray = IntArray(textures.size)
    protected var mipMapCount = Util.calculateMipMapCount(Math.max(width, height))

    fun initialize(gpuContext: GpuContext<OpenGl>) {
        gpuContext.execute("RenderTarget") {
            gpuContext.bindFrameBuffer(frameBuffer)

            configureBorderColor()

            textures.forEachIndexed { index, it ->
                GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, it.id, 0)
                drawBuffers[index] = GL_COLOR_ATTACHMENT0 + index
                renderedTextureHandles[index] = it.handle
                renderedTextures[index] = it.id // TODO: Remove me and the line above me
            }

            GL20.glDrawBuffers(drawBuffers)

            validateFrameBufferState()

            gpuContext.clearColor(clear.x, clear.y, clear.z, clear.w)
        }
    }

    open val renderedTexture: Int
        get() = getRenderedTexture(0)

    private fun getComponentsForFormat(internalFormat: Int): Int {
        return if (GL_RGBA8 == internalFormat || GL_RGBA16F == internalFormat || GL_RGBA32F == internalFormat) {
            GL11.GL_RGBA
        } else if (GL_R32F == internalFormat) {
            GL11.GL_RED
        } else {
            throw IllegalArgumentException("Component identifier missing for internalFormat $internalFormat")
        }
    }

//    TODO: Reimplement if needed?
//    fun resizeTextures(gpuContext: GpuContext<OpenGl>) {
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
//    fun resize(gpuContext: GpuContext<OpenGl>, width: Int, height: Int) {
//        this.width = width
//        this.height = height
//        resizeTextures(gpuContext)
//    }

    open fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
        gpuContext.bindFrameBuffer(frameBuffer)
        gpuContext.viewPort(0, 0, width, height)
        if (clear) {
            gpuContext.clearDepthAndColorBuffer()
        }
    }

    @JvmOverloads
    fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int = 0) {
        GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureID, mipMapLevel)
    }

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    open fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachmentIndex, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, textureId, mipmap)
    }

    fun unuse(gpuContext: GpuContext<OpenGl>) {
        gpuContext.bindFrameBuffer(0)
    }

    fun getRenderedTexture(index: Int): Int {
        return renderedTextures[index]
    }

    fun setRenderedTexture(renderedTexture: Int, index: Int) {
        this.renderedTextures[index] = renderedTexture
    }

    fun setTargetTextureArrayIndex(textureArray: Int, textureIndex: Int) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, textureArray, 0, textureIndex)
    }

    companion object {

        private val LOGGER = Logger.getLogger(RenderTarget::class.java.name)


        operator fun invoke(gpuContext: GpuContext<OpenGl>, renderTargetBuilder: RenderTargetBuilder<*, *>): RenderTarget<Texture2D> {
            val depthBuffer = if (renderTargetBuilder.useDepthBuffer) {
                val dimension = TextureDimension(renderTargetBuilder.width, renderTargetBuilder.height)
                val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
                val textureTarget = GlTextureTarget.TEXTURE_2D
                val internalFormat1 = GL_DEPTH_COMPONENT24
                val (textureId, internalFormat, handle) = allocateTexture(
                        gpuContext,
                        Texture2DUploadInfo(dimension),
                        textureTarget,
                        filterConfig, internalFormat1)

                DepthBuffer(Texture2D(dimension, textureId, textureTarget, internalFormat, handle, filterConfig, GL11.GL_REPEAT, UploadState.UPLOADED))
            } else null
            return RenderTarget(
                    frameBuffer = FrameBuffer(gpuContext, depthBuffer),
                    width = renderTargetBuilder.width,
                    height = renderTargetBuilder.height,
                    textures = renderTargetBuilder.colorAttachments.toTextures(gpuContext, renderTargetBuilder.width, renderTargetBuilder.height),
                    name = renderTargetBuilder.name).apply {

                if(renderTargetBuilder.colorAttachments.isNotEmpty()) initialize(gpuContext)
            }
        }

        private fun validateFrameBufferState() {
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.severe("RenderTarget fucked up")
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
                    LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT")
                } else if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
                    LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT")
                }
                RuntimeException().printStackTrace()
            }
        }

        private fun configureBorderColor() {
            GL11.glTexParameterfv(GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer)
        }

        private fun generateTextureHandle(gpuContext: GpuContext<OpenGl>, textureId: Int): Long {
            val handle = if (gpuContext.isSupported(BindlessTextures)) {
                val handle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
                ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                handle
            } else {
                -1
            }
            return handle
        }

        private fun createTexture(gpuContext: GpuContext<OpenGl>,
                                  textureFilter: TextureFilterConfig,
                                  internalFormat: Int, texture2DUploadInfo:
                                  Texture2DUploadInfo): Texture2D {

            return Texture2D.invoke(
                    gpuContext = gpuContext,
                    info = texture2DUploadInfo,
                    textureFilterConfig = textureFilter,
                    internalFormat = internalFormat
            )
        }
    }
}

fun List<ColorAttachmentDefinition>.toTextures(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): List<Texture2D> = map {
    Texture2D.invoke(
            gpuContext = gpuContext,
            info = Texture2DUploadInfo(dimension = TextureDimension(width, height)),
            textureFilterConfig = it.textureFilter,
            internalFormat = it.internalFormat
    )
}
fun List<ColorAttachmentDefinition>.toCubeMaps(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): List<CubeMap> = map {
    CubeMap.invoke(
            gpuContext = gpuContext,
            filterConfig = it.textureFilter,
            internalFormat = it.internalFormat,
            dimension = TextureDimension(width, height, 6),
            wrapMode = GL11.GL_REPEAT
    )
}

class FrameBuffer(val frameBuffer: Int, val depthBuffer: DepthBuffer<*>?) {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, depthBuffer: DepthBuffer<*>?): FrameBuffer {
            return FrameBuffer(gpuContext.calculate { glGenFramebuffers() }, depthBuffer).apply {
                if(depthBuffer != null) {
                    gpuContext.execute("glFramebufferTexture") {
                        gpuContext.bindFrameBuffer(this)
                        GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.texture.id, 0)
                    }
                }
            }
        }

        val FrontBuffer = FrameBuffer(0, null)
    }
}

class DepthBuffer<T: Texture<*>>(val texture: T)

class RenderBuffer private constructor(val renderBuffer: Int, val width: Int, val height: Int) {
    fun bind(gpuContext: GpuContext<OpenGl>) = gpuContext.execute("BindRenderBuffer") {
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
    }

    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): RenderBuffer {
            val renderBuffer = gpuContext.calculate {
                val renderBuffer = glGenRenderbuffers()
                glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
                renderBuffer
            }
            return RenderBuffer(renderBuffer, width, height)
        }
    }
}
