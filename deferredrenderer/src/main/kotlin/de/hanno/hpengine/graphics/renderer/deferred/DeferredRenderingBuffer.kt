package de.hanno.hpengine.graphics.renderer.deferred


import InternalTextureFormat.*
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.MagFilter
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.getMipMapCountForDimension
import de.hanno.hpengine.sizeInBytes
import de.hanno.hpengine.toCount
import org.joml.Matrix4f
import org.joml.Vector4f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import kotlin.math.max

@Single
class DeferredRenderingBuffer(
    graphicsApi: GraphicsApi,
    config: Config,
    sharedDepthBuffer: SharedDepthBuffer
) {
    private val depthBuffer = sharedDepthBuffer.depthBuffer
    private val width: Int = config.width
    private val height: Int = config.height
    internal val gBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width,
        height,
        (ColorAttachmentDefinitions(
            names = arrayOf("PositionView/Roughness", "Normal/Ambient", "Color/Metallic", "Motion/Depth/Transparency"),
            internalFormat = RGBA16F,
            textureFilter = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
        ).toList() + ColorAttachmentDefinition("Depth/Indices", RGBA32F, TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR))).toTextures(graphicsApi, width, height),
        "GBuffer",
        Vector4f(),
    )

    internal val reflectionBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width,
        height,
        ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), RGBA16F).toList().toTextures(
            graphicsApi,
            width,
            height
        ),
        "Reflection",
        Vector4f(),
    )

    internal val forwardBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = (ColorAttachmentDefinitions(
            arrayOf("DiffuseSpecular", "Revealage"),
            RGBA16F
        ).toList()).toTextures(
            graphicsApi,
            width,
            height
        ),
        name = "Forward",
        clear = Vector4f(),
    )

    internal val laBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = (ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), RGBA16F).toList()).toTextures(
            graphicsApi,
            width,
            height
        ),
        name = "LightAccum",
        clear = Vector4f(),
    )

    internal val finalBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width = width,
        height = height,
        textures = listOf(ColorAttachmentDefinition("Color", RGB8)).toTextures(graphicsApi, width, height),
        name = "Final Image",
        clear = Vector4f(),
    )

    internal val halfScreenBuffer = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(depthBuffer),
        width = width / 2,
        height = height / 2,
        textures = listOf(
            ColorAttachmentDefinition("AO/Scattering", RGBA16F),
            ColorAttachmentDefinition("Indirect", RGBA16F),
            ColorAttachmentDefinition("Bent Normals", RGBA16F)
        ).toTextures(graphicsApi, width / 2, height / 2),
        name = "Half Screen",
        clear = Vector4f(),
    )

    val fullScreenMipmapCount = getMipMapCountForDimension(width, height)
    val exposureBuffer =
        graphicsApi.PersistentShaderStorageBuffer(capacityInBytes = 4.toCount() * Double.sizeInBytes).apply {
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

    val colorReflectivenessTexture: Texture2D = gBuffer.textures[2]

    val motionMap: Int = gBuffer.getRenderedTexture(3)

    val visibilityMap: Int = gBuffer.getRenderedTexture(4)

    val depthAndIndicesMap: Texture2D = gBuffer.textures[4]

    val finalMap: Texture2D = finalBuffer.textures[0]

    val finalMapId: Int = finalBuffer.getRenderedTexture(0)

    fun use(clear: Boolean) {
        gBuffer.use(clear)
    }

    val lightAccumulationBuffer: BackBufferRenderTarget<*> = laBuffer

    val depthBufferTexture = depthBuffer.texture
    val depthBufferTextureId: Int = depthBuffer.texture.id

    val ambientOcclusionScatteringMap: Int = halfScreenBuffer.getRenderedTexture(0)

    val reflectionMap: Int = reflectionBuffer.getRenderedTexture(0)

    val refractedMap: Int = reflectionBuffer.getRenderedTexture(1)

    init {
        Matrix4f().get(identityMatrixBuffer)
        identityMatrixBuffer.rewind()
    }

    companion object {
        var IMPORTANCE_SAMPLE_COUNT = 8

        var USE_COMPUTESHADER_FOR_REFLECTIONS = false

        var RENDER_PROBES_WITH_SECOND_BOUNCE = true
        private val identityMatrixBuffer = BufferUtils.createFloatBuffer(16)
    }
}

var RENDER_PROBES_WITH_FIRST_BOUNCE = true