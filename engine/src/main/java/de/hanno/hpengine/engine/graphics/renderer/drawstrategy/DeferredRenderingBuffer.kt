package de.hanno.hpengine.engine.graphics.renderer.drawstrategy

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.buffer.StorageBuffer
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinitions
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toList
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.util.Util
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30

class DeferredRenderingBuffer(gpuContext: GpuContext<OpenGl>, width: Int, height: Int) {

    val depthBuffer = DepthBuffer(gpuContext, width, height)
    val gBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "GBuffer",
            width = width,
            height = height,
            textures = (ColorAttachmentDefinitions(
                    names = arrayOf("PositionView/Roughness", "Normal/Ambient", "Color/Metallic", "Motion/Depth/Transparency"),
                    internalFormat = GL30.GL_RGBA16F,
                    textureFilter = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
            ).toList() + ColorAttachmentDefinition("Depth/Indices", GL30.GL_RGBA32F)).toTextures(gpuContext, width, height)
        )

    val reflectionBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "Reflection",
            width = width,
            height = height,
            textures = ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), GL30.GL_RGBA16F).toList().toTextures(gpuContext, width, height)
        )

    val forwardBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "Forward",
            width = width,
            height = height,
            textures = (ColorAttachmentDefinitions(arrayOf("DiffuseSpecular", "Revealage"), GL30.GL_RGBA16F).toList()).toTextures(gpuContext, width, height)
        )

    val laBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "LightAccum",
            width = width,
            height = height,
            textures = (ColorAttachmentDefinitions(arrayOf("Diffuse", "Specular"), GL30.GL_RGBA16F).toList()).toTextures(gpuContext, width, height)
        )

    val finalBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "Final Image",
            width = width,
            height = height,
            textures = listOf(ColorAttachmentDefinition("Color", GL30.GL_RGBA8)).toTextures(gpuContext, width, height)
        )

    val halfScreenBuffer = RenderTarget(
            gpuContext,
            FrameBuffer(gpuContext, depthBuffer),
            name = "Half Screen",
            width = width / 2,
            height = height / 2,
            textures = listOf(
                    ColorAttachmentDefinition("AO/Scattering", GL30.GL_RGBA16F),
                    ColorAttachmentDefinition("Indirect", GL30.GL_RGBA16F)
            ).toTextures(gpuContext, width / 2, height / 2)
    )

    val fullScreenMipmapCount = Util.calculateMipMapCount(Math.max(width, height))
    val exposureBuffer = PersistentMappedBuffer(gpuContext, 4 * 8).apply {
        putValues(1f, -1f, 0f, 1f)
    }
    val lightAccumulationMapOneId: Int = laBuffer.getRenderedTexture(0)

    val ambientOcclusionMapId: Int = laBuffer.getRenderedTexture(1)

    val positionMap: Int = gBuffer.getRenderedTexture(0)

    val normalMap: Int = gBuffer.getRenderedTexture(1)

    val colorReflectivenessMap: Int = gBuffer.getRenderedTexture(2)

    val colorReflectivenessTexture: Texture2D = gBuffer.textures[2]

    val motionMap: Int = gBuffer.getRenderedTexture(3)

    val visibilityMap: Int = gBuffer.getRenderedTexture(4)

    val finalMap: Int = finalBuffer.getRenderedTexture(0)

    fun use(gpuContext: GpuContext<OpenGl>, clear: Boolean) {
        gBuffer.use(gpuContext, clear)
    }

    val lightAccumulationBuffer: RenderTarget<*> = laBuffer

    val depthBufferTexture: Int = gBuffer.frameBuffer.depthBuffer!!.texture.id


    val ambientOcclusionScatteringMap: Int = halfScreenBuffer.getRenderedTexture(0)

    val reflectionMap: Int = reflectionBuffer.getRenderedTexture(0)

    val refractedMap: Int = reflectionBuffer.getRenderedTexture(1)

    init {
        Matrix4f().get(identityMatrixBuffer)
        identityMatrixBuffer.rewind()
        gpuContext.getExceptionOnError("rendertarget creation")
    }

    companion object {
        @Volatile
        var IMPORTANCE_SAMPLE_COUNT = 8
        @Volatile
        var USE_COMPUTESHADER_FOR_REFLECTIONS = false
        @JvmField
        @Volatile
        var RENDER_PROBES_WITH_FIRST_BOUNCE = true
        @JvmField
        @Volatile
        var RENDER_PROBES_WITH_SECOND_BOUNCE = true
        private val identityMatrixBuffer = BufferUtils.createFloatBuffer(16)
    }
}