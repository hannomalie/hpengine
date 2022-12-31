package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import InternalTextureFormat.*
import ddsutil.DDSUtil
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.texture.UploadInfo.Texture2DUploadInfo
import jogl.DDSImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO


val compressInternal = true

context(GraphicsApi)
data class OpenGLTexture2D(
    override val dimension: TextureDimension2D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
    override val wrapMode: WrapMode,
    override var uploadState: UploadState
) : Texture2D {

    fun delete() {
        delete(this)
    }
    companion object {

        context(GraphicsApi)
        operator fun invoke(file: File, srgba: Boolean = false): OpenGLTexture2D {
            require(file.exists()) { "File ${file.absolutePath} must exist!" }
            require(file.isFile) { "File ${file.absolutePath} is not a file!" }

            val internalFormat = if (compressInternal) {
                if (srgba) COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else COMPRESSED_RGBA_S3TC_DXT5_EXT
            } else {
                if (srgba) SRGB8_ALPHA8_EXT else RGBA16F
            }

            return if (file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if (compressedTextures) {
                    OpenGLTexture2D(
                        Texture2DUploadInfo(
                            TextureDimension(ddsImage.width, ddsImage.height),
                            ddsImage.getMipMap(0).data,
                            dataCompressed = true,
                            srgba = srgba,
                            internalFormat = internalFormat,
                            textureFilterConfig = TextureFilterConfig(),
                        )
                    )
                } else {
                    OpenGLTexture2D(
                        DDSUtil.decompressTexture(
                            ddsImage.getMipMap(0).data,
                            ddsImage.width,
                            ddsImage.height,
                            ddsImage.compressionFormat
                        ).apply {
                            DDSConverter.run { rescaleToNextPowerOfTwo() }
                        },
                        srgba = srgba,
                    )
                }
            } else OpenGLTexture2D(
                ImageIO.read(file).apply { DDSConverter.run { rescaleToNextPowerOfTwo() } },
                srgba
            )
        }

        context(GraphicsApi)
        operator fun invoke(image: BufferedImage, srgba: Boolean = false): OpenGLTexture2D {
            val buffer = image.toByteBuffer()
            val internalFormat = if (compressInternal) {
                if (srgba) COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else COMPRESSED_RGBA_S3TC_DXT5_EXT
            } else {
                if (srgba) SRGB8_ALPHA8_EXT else RGBA16F
            }
            return OpenGLTexture2D(
                Texture2DUploadInfo(
                    TextureDimension(image.width, image.height), buffer, false, srgba,
                    internalFormat = internalFormat,
                    textureFilterConfig = TextureFilterConfig(),
                )
            )
        }

        private fun BufferedImage.toByteBuffer(): ByteBuffer {
            val width = width
            val height = height

            val pixels = IntArray(width * height)
            getRGB(0, 0, width, height, pixels, 0, width)

            val buffer = BufferUtils.createByteBuffer(width * height * 4) // 4 because RGBA

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[x + y * width]
                    buffer.put((pixel shr 16 and 0xFF).toByte())
                    buffer.put((pixel shr 8 and 0xFF).toByte())
                    buffer.put((pixel and 0xFF).toByte())
                    buffer.put((pixel shr 24 and 0xFF).toByte())
                }
            }

            buffer.flip()
            return buffer
        }

        context(GraphicsApi)
        operator fun invoke(
            info: Texture2DUploadInfo,
            textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
            wrapMode: WrapMode = WrapMode.Repeat
        ) = onGpu {
            val textureAllocationData = allocateTexture(
                info,
                TextureTarget.TEXTURE_2D,
                wrapMode
            )
            OpenGLTexture2D(
                info.dimension,
                textureAllocationData.textureId,
                TextureTarget.TEXTURE_2D,
                info.internalFormat,
                textureAllocationData.handle,
                textureFilterConfig,
                wrapMode,
                UploadState.NOT_UPLOADED
            )
        }.apply {
            CompletableFuture.supplyAsync {
                upload(info, internalFormat)
            }
        }

        context(GraphicsApi)
        private fun OpenGLTexture2D.upload(info: Texture2DUploadInfo, internalFormat: InternalTextureFormat) {
            val usePbo = false
            if (usePbo) {
                uploadWithPixelBuffer(info, internalFormat)
            } else {
                uploadWithoutPixelBuffer(info, internalFormat)
            }
        }

        context(GraphicsApi)
        private fun OpenGLTexture2D.uploadWithPixelBuffer(
            info: Texture2DUploadInfo,
            internalFormat: InternalTextureFormat
        ) {
            val data = info.data ?: return

            val pbo = onGpu {
                PixelBufferObject()
            }
            pbo.put(this@GraphicsApi, data)
            onGpu {
                pbo.unmap(this@GraphicsApi)
                pbo.bind()
                bindTexture(TextureTarget.TEXTURE_2D, id)
                if (info.dataCompressed) {
                    GL13.glCompressedTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        internalFormat.glValue,
                        data.capacity(),
                        0
                    )
                } else {
                    GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        0
                    )
                }
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                pbo.unbind()
                uploadState = UploadState.UPLOADED
            }
        }

        context(GraphicsApi)
        private fun OpenGLTexture2D.uploadWithoutPixelBuffer(info: Texture2DUploadInfo, internalFormat: InternalTextureFormat) = onGpu {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
            val data = info.data
            if(data != null) {
                if (info.dataCompressed) {
                    GL13.glCompressedTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        internalFormat.glValue,
                        data
                    )
                } else {
                    GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        data
                    )
                }
            }
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
            uploadState = UploadState.UPLOADED
        }
    }
}
