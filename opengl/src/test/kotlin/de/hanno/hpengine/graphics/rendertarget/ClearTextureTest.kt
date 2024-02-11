package de.hanno.hpengine.graphics.rendertarget

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.createOpenGLContext
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.toHalfFloat
import format
import io.kotest.matchers.shouldBe
import org.joml.Vector4f
import org.junit.jupiter.api.Test
import org.lwjgl.BufferUtils


val redBufferFloat = BufferUtils.createFloatBuffer(4).apply {
    put(1f)
    put(0f)
    put(0f)
    put(1f)
    rewind()
}
val redBufferHalfFloat = BufferUtils.createShortBuffer(4).apply {
    put(1f.toHalfFloat())
    put(0f.toHalfFloat())
    put(0f.toHalfFloat())
    put(1f.toHalfFloat())
    rewind()
}

class ClearTextureTest {

    @Test
    fun `texture2D texture is cleared`() {
        val (_, graphicsApi) = createOpenGLContext()

        val texture = graphicsApi.Texture2D(
            TextureDimension(500, 500),
            TextureTarget.TEXTURE_2D,
            InternalTextureFormat.RGBA16F,
            TextureFilterConfig(MinFilter.NEAREST),
            WrapMode.Repeat,
            UploadState.Uploaded
        )

        val renderTarget = graphicsApi.RenderTarget(
            graphicsApi.FrameBuffer(
                graphicsApi.DepthBuffer(
                    texture.dimension.width,
                    texture.dimension.height,
                )
            ),
            500,
            500,
            listOf(texture),
            "RenderTarget",
            Vector4f(1f, 0f, 0f, 0f)
        )
        val buffer = BufferUtils.createByteBuffer(texture.dimension.width * texture.dimension.height * 4 * 32)
        val floatBuffer = buffer.apply { rewind() }.asFloatBuffer()
        val halfBuffer = buffer.apply { rewind() }.asShortBuffer()

        graphicsApi.clearTexImage(
            texture.id,
            texture.internalFormat.format,
            0,
            TexelComponentType.Float,
            redBufferFloat
        )
        graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
        graphicsApi.finish()
        Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(1f, 0f, 0f, 1f)

        graphicsApi.clearTexImage(texture.id, texture.internalFormat.format, 0, TexelComponentType.Float)
        graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
        graphicsApi.finish()
        Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(0f, 0f, 0f, 0f)

        graphicsApi.clearColor(1f, 1f, 0f, 0f)
        renderTarget.use(true)
        // TODO: Implement overloads per texel component type
        graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
        graphicsApi.finish()
        Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(1f, 1f, 0f, 0f)
    }

    @Test
    fun `cubeMapArray texture is cleared`() {
        val (_, graphicsApi) = createOpenGLContext()

        val texture = graphicsApi.CubeMapArray(
            TextureDimension(500, 500, 4),
            TextureFilterConfig(MinFilter.NEAREST),
            InternalTextureFormat.RGBA16F,
            WrapMode.Repeat
        )

        val cubeMapArrayRenderTarget = graphicsApi.RenderTarget(
            graphicsApi.FrameBuffer(
                graphicsApi.createDepthBuffer(
                    graphicsApi.CubeMapArray(
                        TextureDimension(
                            texture.dimension.width,
                            texture.dimension.height,
                            texture.dimension.depth
                        ),
                        TextureFilterConfig(minFilter = MinFilter.NEAREST),
                        internalFormat = InternalTextureFormat.DEPTH_COMPONENT24,
                        wrapMode = WrapMode.ClampToEdge
                    )
                )
            ),
            500,
            500,
            listOf(texture),
            "PointLightCubeMapArrayRenderTarget",
            Vector4f(1f, 0f, 0f, 0f)
        )
        val buffer =
            BufferUtils.createByteBuffer(texture.dimension.depth * texture.dimension.width * texture.dimension.height * 4 * 32)
        val floatBuffer = buffer.asFloatBuffer()


        graphicsApi.clearTexImage(
            texture.id,
            texture.internalFormat.format,
            0,
            TexelComponentType.Float,
            redBufferFloat
        )
        graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
        graphicsApi.finish()
        Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(1f, 0f, 0f, 1f)

        graphicsApi.clearColor(0f, 1f, 0f, 0f)
        cubeMapArrayRenderTarget.use(true)

        graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
        Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(0f, 1f, 0f, 0f)

        cubeMapArrayRenderTarget.cubeMapFaceViews.first().let { texture ->
            graphicsApi.getTextureData(texture, 0, texture.internalFormat.format, TexelComponentType.Float, buffer)
            Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(0f, 1f, 0f, 0f)
        }
        cubeMapArrayRenderTarget.cubeMapViews.first().let { texture ->
            graphicsApi.getTextureData(
                texture,
                CubeMapFace.POSITIVE_X,
                0,
                texture.internalFormat.format,
                TexelComponentType.Float,
                buffer
            )
            Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]) shouldBe Vector4f(0f, 1f, 0f, 0f)
        }
    }
}