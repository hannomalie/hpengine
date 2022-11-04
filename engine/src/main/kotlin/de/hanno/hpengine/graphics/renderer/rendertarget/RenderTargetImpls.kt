package de.hanno.hpengine.graphics.renderer.rendertarget


import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.util.Util
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL30.*
import java.util.logging.Logger
import kotlin.math.max

val borderColorBuffer = BufferUtils.createFloatBuffer(4).apply {
    val borderColors = floatArrayOf(0f, 0f, 0f, 1f)
    put(borderColors)
    rewind()
}

operator fun RenderTarget.Companion.invoke(
    gpuContext: GpuContext,
    frameBuffer: FrameBuffer,
    width: Int = 1280,
    height: Int = 720,
    textures: List<OpenGLTexture2D> = emptyList(),
    name: String,
    clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
): RenderTarget2D {

    return RenderTarget2D(gpuContext, RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear))
}

operator fun RenderTarget.Companion.invoke(
    gpuContext: GpuContext,
    frameBuffer: FrameBuffer,
    width: Int = 1280,
    height: Int = 720,
    textures: List<OpenGLCubeMap> = emptyList(),
    name: String,
    clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
): CubeMapRenderTarget {

    return CubeMapRenderTarget(
        gpuContext,
        RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear)
    )
}

operator fun RenderTarget.Companion.invoke(
    gpuContext: GpuContext,
    frameBuffer: FrameBuffer,
    width: Int = 1280,
    height: Int = 720,
    textures: List<OpenGLCubeMapArray> = emptyList(),
    name: String,
    clear: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
): CubeMapArrayRenderTarget {

    return CubeMapArrayRenderTarget(
        gpuContext,
        RenderTargetImpl(gpuContext, frameBuffer, width, height, textures, name, clear)
    )
}

class CubeMapRenderTarget(
    gpuContext: GpuContext,
    renderTarget: RenderTarget<OpenGLCubeMap>
) : RenderTarget<OpenGLCubeMap> by renderTarget {
    init {
        gpuContext.register(this)
    }
}

class RenderTarget2D(
    gpuContext: GpuContext,
    renderTarget: RenderTarget<OpenGLTexture2D>
) : RenderTarget<OpenGLTexture2D> by renderTarget {
    init {
        gpuContext.register(this)
    }
}

internal fun <T : Texture> RenderTarget(
    gpuContext: GpuContext,
    frameBuffer: FrameBuffer,
    width: Int = 1280,
    height: Int = 720,
    textures: List<T> = emptyList(),
    name: String,
    clear: Vector4f = Vector4f(
        0.0f,
        0.0f,
        0.0f,
        0.0f
    )
) = RenderTargetImpl(
    gpuContext,
    frameBuffer,
    width,
    height,
    textures,
    name,
    clear,
)

class RenderTargetImpl<T : Texture>(
    private val gpuContext: GpuContext,
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
) : RenderTarget<T> {

    override var renderedTextures: IntArray = IntArray(textures.size)
    override var renderedTextureHandles: LongArray = LongArray(textures.size)
    override var drawBuffers: IntArray = IntArray(textures.size)
    override var mipMapCount = Util.calculateMipMapCount(max(width, height))

    // TODO: This is probably not the nicest way to implement this but i am in a hurry :)
    override val factorsForDebugRendering = textures.map { 1f }.toMutableList()

    init {
        gpuContext.invoke {
            gpuContext.bindFrameBuffer(frameBuffer)

//            TODO: Is this needed anymore?
//            configureBorderColor()

            if (textures.first() is OpenGLCubeMapArray) {
                textures.forEachIndexed { index, it ->
                    GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index, 0, 0, 0)
                }
            } else {
                textures.forEachIndexed { index, it ->
                    GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, it.id, 0)
                    drawBuffers[index] = GL_COLOR_ATTACHMENT0 + index
                    renderedTextureHandles[index] = it.handle
                    renderedTextures[index] = it.id // TODO: Remove me and the line above me
                }
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

    override fun use(gpuContext: GpuContext, clear: Boolean) = gpuContext.invoke {
        gpuContext.bindFrameBuffer(frameBuffer)
        gpuContext.viewPort(0, 0, width, height)
        if (clear) {
            gpuContext.clearDepthAndColorBuffer()
        }
    }

    override fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int) {
        GL32.glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureID, mipMapLevel)
    }

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    override fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0 + attachmentIndex,
            GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index,
            textureId,
            mipmap
        )
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

        fun RenderTargetImpl<*>.validateFrameBufferState() {
            val frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.severe("RenderTarget fucked up")
                when (frameBufferStatus) {
                    GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT")
                    GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT")
                    GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER")
                    GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER")
                    GL30.GL_FRAMEBUFFER_UNSUPPORTED -> LOGGER.severe("GL_FRAMEBUFFER_UNSUPPORTED")
                    GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> LOGGER.severe("GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE")
                    GL30.GL_FRAMEBUFFER_UNDEFINED -> LOGGER.severe("GL_FRAMEBUFFER_UNDEFINED")
                }
                throw RuntimeException("Rendertarget $name fucked up")
            }
        }

        fun configureBorderColor() {
            GL11.glTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer)
        }

        fun generateTextureHandle(gpuContext: GpuContext, textureId: Int): Long = if (gpuContext.isSupported(
                BindlessTextures
            )
        ) {
            val handle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
            ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
            handle
        } else -1

        fun createTexture(
            gpuContext: GpuContext,
            textureFilter: TextureFilterConfig,
            internalFormat: Int, texture2DUploadInfo:
            OpenGLTexture2D.TextureUploadInfo.Texture2DUploadInfo
        ): OpenGLTexture2D {

            return OpenGLTexture2D.invoke(
                gpuContext = gpuContext,
                info = texture2DUploadInfo,
                textureFilterConfig = textureFilter,
                internalFormat = internalFormat
            )
        }

        fun getComponentsForFormat(internalFormat: Int): Int = when (internalFormat) {
            GL11.GL_RGBA8, GL_RGBA16F, GL_RGBA32F -> GL11.GL_RGBA
            GL_R32F -> GL11.GL_RED
            else -> throw IllegalArgumentException("Component identifier missing for internalFormat $internalFormat")
        }
    }

    override fun using(gpuContext: GpuContext, clear: Boolean, block: () -> Unit) = try {
        use(gpuContext, clear)
        block()
    } finally {
        unUse()
    }
}

fun List<ColorAttachmentDefinition>.toTextures(
    gpuContext: GpuContext,
    width: Int,
    height: Int
): List<OpenGLTexture2D> = map {
    OpenGLTexture2D(
        gpuContext = gpuContext,
        info = OpenGLTexture2D.TextureUploadInfo.Texture2DUploadInfo(dimension = TextureDimension(width, height)),
        textureFilterConfig = it.textureFilter,
        internalFormat = it.internalFormat
    )
}

fun List<ColorAttachmentDefinition>.toCubeMaps(gpuContext: GpuContext, width: Int, height: Int): List<OpenGLCubeMap> =
    map {
        OpenGLCubeMap(
            gpuContext = gpuContext,
            filterConfig = it.textureFilter,
            internalFormat = it.internalFormat,
            dimension = TextureDimension(width, height),
            wrapMode = GL_REPEAT
        )
    }

fun List<ColorAttachmentDefinition>.toCubeMapArrays(
    gpuContext: GpuContext,
    width: Int,
    height: Int,
    depth: Int
): List<OpenGLCubeMapArray> = map {
    OpenGLCubeMapArray(
        gpuContext = gpuContext,
        filterConfig = it.textureFilter,
        internalFormat = it.internalFormat,
        dimension = TextureDimension(width, height, depth),
        wrapMode = GL_REPEAT
    )
}

class DepthBuffer<T : Texture>(val texture: T) {
    companion object {
        operator fun invoke(gpuContext: GpuContext, width: Int, height: Int): DepthBuffer<OpenGLTexture2D> {
            val dimension = TextureDimension(width, height)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            val textureTarget = TextureTarget.TEXTURE_2D
            val internalFormat1 = GL14.GL_DEPTH_COMPONENT24
            val (textureId, internalFormat, handle) = allocateTexture(
                gpuContext,
                OpenGLTexture2D.TextureUploadInfo.Texture2DUploadInfo(dimension),
                textureTarget,
                filterConfig, internalFormat1
            )

            DepthBuffer(
                OpenGLTexture2D(
                    dimension,
                    textureId,
                    textureTarget,
                    internalFormat,
                    handle,
                    filterConfig,
                    GL_REPEAT,
                    UploadState.UPLOADED
                )
            )

            return DepthBuffer(
                OpenGLTexture2D(
                    dimension,
                    textureId,
                    textureTarget,
                    internalFormat,
                    handle,
                    filterConfig,
                    GL_REPEAT,
                    UploadState.UPLOADED
                )
            )
        }
    }
}

class RenderBuffer private constructor(val renderBuffer: Int, val width: Int, val height: Int) {
    fun bind(gpuContext: GpuContext) = gpuContext.invoke {
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
    }

    companion object {
        operator fun invoke(gpuContext: GpuContext, width: Int, height: Int): RenderBuffer {
            val renderBuffer = gpuContext.invoke {
                val renderBuffer = glGenRenderbuffers()
                glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
                renderBuffer
            }
            return RenderBuffer(renderBuffer, width, height)
        }
    }
}