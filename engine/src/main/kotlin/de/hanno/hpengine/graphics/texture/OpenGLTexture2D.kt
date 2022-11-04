package de.hanno.hpengine.graphics.texture

import ddsutil.DDSUtil
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.texture.UploadInfo.Texture2DUploadInfo
import jogl.DDSImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

data class OpenGLTexture2D(
    override val dimension: TextureDimension2D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: Int,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
    override val wrapMode: Int,
    override var uploadState: UploadState
) : Texture2D {
    companion object {
        operator fun invoke(gpuContext: GpuContext, file: File, path: String, srgba: Boolean = false): OpenGLTexture2D {
            val fileAsDds = File(path.split(".")[0] + ".dds")
            if (!file.exists() || !file.isFile) {
                throw IllegalStateException("Cannot load file $file as texture as it doesn't exist")
            }
            return if (file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if (compressedTextures) {
                    val image = Texture2DUploadInfo(
                        TextureDimension(ddsImage.width, ddsImage.height),
                        ddsImage.getMipMap(0).data,
                        dataCompressed = true,
                        srgba = srgba
                    )
                    OpenGLTexture2D(gpuContext, image)
                } else {
                    OpenGLTexture2D(
                        gpuContext,
                        DDSUtil.decompressTexture(
                            ddsImage.getMipMap(0).data,
                            ddsImage.width,
                            ddsImage.height,
                            ddsImage.compressionFormat
                        ).apply {
                            with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() }
                        })
                }
            } else OpenGLTexture2D(
                gpuContext,
                ImageIO.read(file).apply { with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() } },
                srgba
            )

        }

        operator fun invoke(gpuContext: GpuContext, image: BufferedImage, srgba: Boolean = false): OpenGLTexture2D {
            val buffer = image.toByteBuffer()
            val image1 = Texture2DUploadInfo(TextureDimension(image.width, image.height), buffer, srgba = srgba)
            return OpenGLTexture2D(
                gpuContext,
                Texture2DUploadInfo(TextureDimension(image.width, image.height), buffer, srgba = srgba),
                internalFormat = if (image1.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
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

        operator fun invoke(
            gpuContext: GpuContext,
            info: Texture2DUploadInfo,
            textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
            internalFormat: Int = if (info.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT,
            wrapMode: Int = GL12.GL_REPEAT
        ): OpenGLTexture2D {
            return gpuContext.invoke {
                val (textureId, internalFormat, handle) = gpuContext.allocateTexture(
                    info,
                    TextureTarget.TEXTURE_2D,
                    textureFilterConfig,
                    internalFormat,
                    wrapMode
                )
                if (gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                OpenGLTexture2D(
                    dimension = info.dimension,
                    id = textureId,
                    target = TextureTarget.TEXTURE_2D,
                    textureFilterConfig = textureFilterConfig,
                    internalFormat = internalFormat,
                    handle = handle,
                    wrapMode = wrapMode,
                    uploadState = UploadState.NOT_UPLOADED
                ).apply {
                    CompletableFuture.supplyAsync {
                        upload(gpuContext, info, internalFormat)
                    }
                }
            }
        }

        private fun OpenGLTexture2D.upload(gpuContext: GpuContext, info: Texture2DUploadInfo, internalFormat: Int) {
            val usePbo = true
            if (usePbo) {
                uploadWithPixelBuffer(gpuContext, info, internalFormat)
            } else {
                uploadWithoutPixelBuffer(gpuContext, info, internalFormat)
            }
        }

        private fun OpenGLTexture2D.uploadWithPixelBuffer(
            gpuContext: GpuContext,
            info: Texture2DUploadInfo,
            internalFormat: Int
        ) {
            val data = info.data ?: return

            val pbo = gpuContext.invoke {
                PixelBufferObject()
            }
            pbo.put(gpuContext, data)
            gpuContext.invoke {
                pbo.unmap(gpuContext)
                pbo.bind()
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                if (info.dataCompressed) {
                    GL13.glCompressedTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        internalFormat,
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
                gpuContext.getExceptionOnError("After glTexSubImage")
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                pbo.unbind()
                uploadState = UploadState.UPLOADED
            }
        }

        private fun OpenGLTexture2D.uploadWithoutPixelBuffer(
            gpuContext: GpuContext,
            info: Texture2DUploadInfo,
            internalFormat: Int
        ) {
            gpuContext.invoke {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                if (info.dataCompressed) {
                    GL13.glCompressedTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        internalFormat,
                        info.data
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
                        info.data
                    )
                }
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                uploadState = UploadState.UPLOADED
            }
        }

    }

}
