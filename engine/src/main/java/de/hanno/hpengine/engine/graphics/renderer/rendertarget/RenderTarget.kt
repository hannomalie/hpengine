package de.hanno.hpengine.engine.graphics.renderer.rendertarget

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
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
import org.lwjgl.opengl.GL32.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL32.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL32.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL32.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL32.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT
import org.lwjgl.opengl.GL32.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT
import org.lwjgl.opengl.GL32.GL_R32F
import org.lwjgl.opengl.GL32.GL_RENDERBUFFER
import org.lwjgl.opengl.GL32.GL_RGBA16F
import org.lwjgl.opengl.GL32.GL_RGBA32F
import org.lwjgl.opengl.GL32.glBindRenderbuffer
import org.lwjgl.opengl.GL32.glCheckFramebufferStatus
import org.lwjgl.opengl.GL32.glFramebufferTexture
import org.lwjgl.opengl.GL32.glFramebufferTexture2D
import org.lwjgl.opengl.GL32.glFramebufferTextureLayer
import org.lwjgl.opengl.GL32.glGenFramebuffers
import org.lwjgl.opengl.GL32.glGenRenderbuffers
import java.util.logging.Logger
import kotlin.math.max

val borderColorBuffer = BufferUtils.createFloatBuffer(4).apply {
    val borderColors = floatArrayOf(0f, 0f, 0f, 1f)
    put(borderColors)
    rewind()
}

interface FrontBufferTarget {
    val frameBuffer: FrameBuffer
    val width: Int
    val height: Int
    val name: String
        get() = "FrontBufferTarget"
    val clear: Vector4f
    fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean)
}

interface RenderTarget<T : Texture> : FrontBufferTarget {
    val textures: List<T>
    var renderedTextures: IntArray
    var renderedTextureHandles: LongArray
    var drawBuffers: IntArray
    var mipMapCount: Int
    val renderedTexture: Int

    fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int = 0)

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    open fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int)


    fun using(gpuContext: GpuContext<OpenGl>, clear: Boolean, block: () -> Unit) = try {
        use(gpuContext, clear)
        block()
    } finally {
        unUse()
    }

    fun unUse()
    fun getRenderedTexture(index: Int): Int
    fun setRenderedTexture(renderedTexture: Int, index: Int)
    fun setTargetTextureArrayIndex(textureArray: Int, textureIndex: Int)

    companion object {
        @JvmOverloads
        @JvmName("create2D")
        operator fun invoke(gpuContext: GpuContext<OpenGl>,
                            frameBuffer: FrameBuffer,
                            width: Int = 1280,
                            height: Int = 720,
                            textures: List<Texture2D> = emptyList(),
                            name: String,
                            clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)): RenderTarget2D {

            return RenderTarget2D(gpuContext, RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear))
        }

        @JvmOverloads
        @JvmName("createCubeMap")
        operator fun invoke(gpuContext: GpuContext<OpenGl>,
                            frameBuffer: FrameBuffer,
                            width: Int = 1280,
                            height: Int = 720,
                            textures: List<CubeMap> = emptyList(),
                            name: String,
                            clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)): CubeMapRenderTarget {

            return CubeMapRenderTarget(gpuContext, RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear))
        }
        @JvmOverloads
        @JvmName("createCubeMapArray")
        operator fun invoke(gpuContext: GpuContext<OpenGl>,
                            frameBuffer: FrameBuffer,
                            width: Int = 1280,
                            height: Int = 720,
                            textures: List<CubeMapArray> = emptyList(),
                            name: String,
                            clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)): CubeMapArrayRenderTarget {

            return CubeMapArrayRenderTarget(gpuContext, RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear))
        }
    }

}
class CubeMapRenderTarget(gpuContext: GpuContext<OpenGl>,
                          renderTarget: RenderTarget<CubeMap>): RenderTarget<CubeMap> by renderTarget {
    init {
        gpuContext.register(this)
    }
}
class RenderTarget2D(gpuContext: GpuContext<OpenGl>,
                      renderTarget: RenderTarget<Texture2D>): RenderTarget<Texture2D> by renderTarget {
    init {
        gpuContext.register(this)
    }
}
private class RenderTargetImpl<T : Texture> @JvmOverloads constructor(private val gpuContext: GpuContext<OpenGl>,
                                                              override val frameBuffer: FrameBuffer,
                                                              override val width: Int = 1280,
                                                              override val height: Int = 720,
                                                              override val textures: List<T> = emptyList(),
                                                              override val name: String,
                                                              override val clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)) : RenderTarget<T> {

    override var renderedTextures: IntArray = IntArray(textures.size)
    override var renderedTextureHandles: LongArray = LongArray(textures.size)
    override var drawBuffers: IntArray = IntArray(textures.size)
    override var mipMapCount = Util.calculateMipMapCount(max(width, height))

    init {
        gpuContext.invoke {
            gpuContext.bindFrameBuffer(frameBuffer)

//            TODO: Is this needed anymore?
//            configureBorderColor()

            textures.forEachIndexed { index, it ->
                glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, it.id, 0)
                drawBuffers[index] = GL_COLOR_ATTACHMENT0 + index
                renderedTextureHandles[index] = it.handle
                renderedTextures[index] = it.id // TODO: Remove me and the line above me
            }

            GL20.glDrawBuffers(drawBuffers)

            validateFrameBufferState()

            gpuContext.clearColor(clear.x, clear.y, clear.z, clear.w)
        }

        gpuContext.getExceptionOnError("rendertarget creation failed $name")
    }

    override val renderedTexture: Int
        get() = getRenderedTexture(0)

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

    override fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) = gpuContext.invoke {
        gpuContext.bindFrameBuffer(frameBuffer)
        gpuContext.viewPort(0, 0, width, height)
        if (clear) {
            gpuContext.clearDepthAndColorBuffer()
        }
    }

    @JvmOverloads
    override fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int) {
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureID, mipMapLevel)
    }

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    override fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachmentIndex, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, textureId, mipmap)
    }

    override fun unUse() {
        this.gpuContext.bindFrameBuffer(0)
    }

    override fun getRenderedTexture(index: Int): Int {
        return renderedTextures[index]
    }

    override fun setRenderedTexture(renderedTexture: Int, index: Int) {
        this.renderedTextures[index] = renderedTexture
    }

    override fun setTargetTextureArrayIndex(textureArray: Int, textureIndex: Int) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, textureArray, 0, textureIndex)
    }

    companion object {

        val LOGGER = Logger.getLogger(RenderTarget::class.java.name)

        fun validateFrameBufferState() {
            val frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.severe("RenderTarget fucked up")
                when (frameBufferStatus) {
                    GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT")
                    GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT")
                    GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER")
                    GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER")
                    GL_FRAMEBUFFER_UNSUPPORTED -> LOGGER.severe("GL_FRAMEBUFFER_UNSUPPORTED")
                    GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE")
                    GL_FRAMEBUFFER_UNDEFINED -> LOGGER.severe("GL_FRAMEBUFFER_UNDEFINED")
                }
                throw RuntimeException()
            }
        }

        fun configureBorderColor() {
            GL11.glTexParameterfv(GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer)
        }

        fun generateTextureHandle(gpuContext: GpuContext<OpenGl>, textureId: Int): Long {
            val handle = if (gpuContext.isSupported(BindlessTextures)) {
                val handle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
                ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                handle
            } else {
                -1
            }
            return handle
        }

        fun createTexture(gpuContext: GpuContext<OpenGl>,
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

        fun getComponentsForFormat(internalFormat: Int): Int {
            return if (GL_RGBA8 == internalFormat || GL_RGBA16F == internalFormat || GL_RGBA32F == internalFormat) {
                GL11.GL_RGBA
            } else if (GL_R32F == internalFormat) {
                GL11.GL_RED
            } else {
                throw IllegalArgumentException("Component identifier missing for internalFormat $internalFormat")
            }
        }
    }

    override fun using(gpuContext: GpuContext<OpenGl>, clear: Boolean, block: () -> Unit) = try {
        use(gpuContext, clear)
        block()
    } finally {
        unUse()
    }
}

fun List<ColorAttachmentDefinition>.toTextures(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): List<Texture2D> = map {
    Texture2D(
            gpuContext = gpuContext,
            info = Texture2DUploadInfo(dimension = TextureDimension(width, height)),
            textureFilterConfig = it.textureFilter,
            internalFormat = it.internalFormat
    )
}

fun List<ColorAttachmentDefinition>.toCubeMaps(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): List<CubeMap> = map {
    CubeMap(
            gpuContext = gpuContext,
            filterConfig = it.textureFilter,
            internalFormat = it.internalFormat,
            dimension = TextureDimension(width, height),
            wrapMode = GL11.GL_REPEAT
    )
}

class FrameBuffer(val frameBuffer: Int, val depthBuffer: DepthBuffer<*>?) {

    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, depthBuffer: DepthBuffer<*>?): FrameBuffer {
            return FrameBuffer(gpuContext.invoke { glGenFramebuffers() }, depthBuffer).apply {
                if (depthBuffer != null) {
                    gpuContext.invoke {
                        gpuContext.bindFrameBuffer(this)
                        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.texture.id, 0)
                    }
                }
            }
        }

        val FrontBuffer = FrameBuffer(0, null)
    }
}

class DepthBuffer<T : Texture>(val texture: T) {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): DepthBuffer<Texture2D> {
            val dimension = TextureDimension(width, height)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            val textureTarget = GlTextureTarget.TEXTURE_2D
            val internalFormat1 = GL_DEPTH_COMPONENT24
            val (textureId, internalFormat, handle) = allocateTexture(
                    gpuContext,
                    Texture2DUploadInfo(dimension),
                    textureTarget,
                    filterConfig, internalFormat1)

            DepthBuffer(Texture2D(dimension, textureId, textureTarget, internalFormat, handle, filterConfig, GL11.GL_REPEAT, UploadState.UPLOADED))

            return DepthBuffer(Texture2D(dimension, textureId, textureTarget, internalFormat, handle, filterConfig, GL11.GL_REPEAT, UploadState.UPLOADED))
        }
    }
}

class RenderBuffer private constructor(val renderBuffer: Int, val width: Int, val height: Int) {
    fun bind(gpuContext: GpuContext<OpenGl>) = gpuContext.invoke {
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
    }

    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, width: Int, height: Int): RenderBuffer {
            val renderBuffer = gpuContext.invoke {
                val renderBuffer = glGenRenderbuffers()
                glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
                renderBuffer
            }
            return RenderBuffer(renderBuffer, width, height)
        }
    }
}
