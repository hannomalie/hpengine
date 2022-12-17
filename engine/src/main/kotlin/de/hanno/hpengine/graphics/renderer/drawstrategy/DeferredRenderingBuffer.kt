package de.hanno.hpengine.graphics.renderer.drawstrategy


import InternalTextureFormat.*
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTargetImpl
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import kotlin.math.max

context(GpuContext)
class DeferredRenderingBuffer(width: Int, height: Int, val depthBuffer: DepthBuffer<*>) {

    internal val gBuffer = RenderTargetImpl(
        OpenGLFrameBuffer(depthBuffer),
        width,
        height,
        (ColorAttachmentDefinitions(
            names = arrayOf("PositionView/Roughness", "Normal/Ambient", "Color/Metallic", "Motion/Depth/Transparency"),
            internalFormat = RGBA16F,
            textureFilter = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
        ).toList() + ColorAttachmentDefinition("Depth/Indices", RGBA32F)).toTextures(width, height),
        "GBuffer",
    )

    internal val reflectionBuffer = RenderTarget(
        OpenGLFrameBuffer(depthBuffer),
        width,
        height,
        ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), RGBA16F).toList().toTextures(
            width,
            height
        ),
        "Reflection"
    )

    internal val forwardBuffer = RenderTarget(
        OpenGLFrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = (ColorAttachmentDefinitions(
            arrayOf("DiffuseSpecular", "Revealage"),
            RGBA16F
        ).toList()).toTextures(
            width,
            height
        ),
        name = "Forward"
    )

    internal val laBuffer = RenderTarget(
        OpenGLFrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = (ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), RGBA16F).toList()).toTextures(
            width,
            height
        ),
        name = "LightAccum"
    )

    internal val finalBuffer = RenderTarget(
        OpenGLFrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = listOf(ColorAttachmentDefinition("Color", RGB8)).toTextures(width, height),
        name = "Final Image"
    )

    internal val halfScreenBuffer = RenderTarget(
        OpenGLFrameBuffer(depthBuffer),
        width = width / 2,
        height = height / 2,
        textures = listOf(
            ColorAttachmentDefinition("AO/Scattering", RGBA16F),
            ColorAttachmentDefinition("Indirect", RGBA16F),
            ColorAttachmentDefinition("Bent Normals", RGBA16F)
        ).toTextures(width / 2, height / 2),
        name = "Half Screen"
    )

    val fullScreenMipmapCount = calculateMipMapCount(max(width, height))
    val exposureBuffer =
        PersistentShaderStorageBuffer(capacityInBytes = 4 * Double.SIZE_BYTES).apply {
            buffer.rewind()
            buffer.asDoubleBuffer().apply {
                put(1.0)
                put(-1.0)
                put(0.0)
                put(1.0)
            }
        }
    val lightAccumulationMapOneId: Int = laBuffer.getRenderedTexture(0)

    val ambientOcclusionMapId: Int = laBuffer.getRenderedTexture(1)

    val positionMap: Int = gBuffer.getRenderedTexture(0)

    val normalMap: Int = gBuffer.getRenderedTexture(1)

    val colorReflectivenessMap: Int = gBuffer.getRenderedTexture(2)

    val colorReflectivenessTexture: OpenGLTexture2D = gBuffer.textures[2]

    val motionMap: Int = gBuffer.getRenderedTexture(3)

    val visibilityMap: Int = gBuffer.getRenderedTexture(4)

    val depthAndIndicesMap: OpenGLTexture2D = gBuffer.textures[4]

    val finalMap: OpenGLTexture2D = finalBuffer.textures[0]

    val finalMapId: Int = finalBuffer.getRenderedTexture(0)

    fun use(clear: Boolean) {
        gBuffer.use(clear)
    }

    val lightAccumulationBuffer: BackBufferRenderTarget<*> = laBuffer

    val depthBufferTexture = gBuffer.frameBuffer.depthBuffer!!.texture
    val depthBufferTextureId: Int = gBuffer.frameBuffer.depthBuffer!!.texture.id


    val ambientOcclusionScatteringMap: Int = halfScreenBuffer.getRenderedTexture(0)

    val reflectionMap: Int = reflectionBuffer.getRenderedTexture(0)

    val refractedMap: Int = reflectionBuffer.getRenderedTexture(1)

    init {
        Matrix4f().get(identityMatrixBuffer)
        identityMatrixBuffer.rewind()
    }

    companion object {
        @Volatile
        var IMPORTANCE_SAMPLE_COUNT = 8

        @Volatile
        var USE_COMPUTESHADER_FOR_REFLECTIONS = false

        @JvmField
        @Volatile
        var RENDER_PROBES_WITH_SECOND_BOUNCE = true
        private val identityMatrixBuffer = BufferUtils.createFloatBuffer(16)
    }
}

@Volatile
var RENDER_PROBES_WITH_FIRST_BOUNCE = true