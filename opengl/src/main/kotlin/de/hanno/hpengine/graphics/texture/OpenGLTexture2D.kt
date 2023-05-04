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
        operator fun invoke(file: File, path: String = file.absolutePath, srgba: Boolean = false): FileBasedTexture2D {
            require(file.exists()) { "File ${file.absolutePath} must exist!" }
            require(file.isFile) { "File ${file.absolutePath} is not a file!" }

            val internalFormat = if (compressInternal) {
                if (srgba) COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else COMPRESSED_RGBA_S3TC_DXT5_EXT
            } else {
                if (srgba) SRGB8_ALPHA8_EXT else RGBA16F
            }

            val openGLTexture = if (file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if (compressedTextures) {
                    OpenGLTexture2D(
                        UploadInfo.CompleteTexture2DUploadInfo(
                            TextureDimension(ddsImage.width, ddsImage.height),
                            ddsImage.allMipMaps.map { it.data },
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

            return FileBasedTexture2D(
                path,
                file,
                openGLTexture
            )
        }

        context(GraphicsApi)
        operator fun invoke(image: BufferedImage, srgba: Boolean = false): OpenGLTexture2D {
            val internalFormat = if (compressInternal) {
                if (srgba) COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else COMPRESSED_RGBA_S3TC_DXT5_EXT
            } else {
                if (srgba) SRGB8_ALPHA8_EXT else RGBA16F
            }

            return OpenGLTexture2D(
                UploadInfo.SimpleTexture2DUploadInfo(
                    TextureDimension(image.width, image.height), null, false, srgba,
                    internalFormat = internalFormat,
                    textureFilterConfig = TextureFilterConfig(),
                )
            ).apply {
                uploadAsync(image, srgba, internalFormat)
            }
        }

        context(GraphicsApi)
        internal fun OpenGLTexture2D.uploadAsync(
            image: BufferedImage,
            srgba: Boolean,
            internalFormat: InternalTextureFormat
        ) {
            val mipMapCount = TextureDimension(image.width, image.height).getMipMapCount()
            val widths = mutableListOf<Int>()
            val heights = mutableListOf<Int>()
            var nextWidth = (image.width * 0.5).toInt()
            var nextHeight = (image.width * 0.5).toInt()
            (0 until mipMapCount - 1).forEach {
                widths.add(nextWidth)
                heights.add(nextWidth)
                nextWidth = (nextWidth * 0.5).toInt()
                nextHeight = (nextHeight * 0.5).toInt()
            }
            CompletableFuture.supplyAsync {
                val mipMapData = widths.mapIndexed { index, it ->
                    LazyTextureData(it, heights[index]) { image.resize(it).toByteBuffer() }
                }
                val data = listOf(LazyTextureData(image.width, image.height) { image.toByteBuffer() }) + mipMapData

                val info = UploadInfo.LazyTexture2DUploadInfo(
                    TextureDimension(image.width, image.height), data, false, srgba,
                    internalFormat = internalFormat,
                    textureFilterConfig = TextureFilterConfig(),
                    mipMapCount,
                )

                upload(info)
            }
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
                UploadState.NotUploaded
            )
        }

        context(GraphicsApi)
        internal fun OpenGLTexture2D.upload(info: Texture2DUploadInfo) {
            val usePbo = false // the current state of texture streaming doesn't make pbos feasible
            if (usePbo) {
                uploadWithPixelBuffer(info)
            } else {
                uploadWithoutPixelBuffer(info)
            }
        }

        context(GraphicsApi)
        private fun OpenGLTexture2D.uploadWithPixelBuffer(info: Texture2DUploadInfo) {
            pixelBufferObjectPool.scheduleUpload(info, this)
        }

        context(GraphicsApi)
        private fun OpenGLTexture2D.uploadWithoutPixelBuffer(info: Texture2DUploadInfo) = onGpu {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)

            val data = when(info) {
                is UploadInfo.CompleteTexture2DUploadInfo -> info.data.firstOrNull()
                is UploadInfo.SimpleTexture2DUploadInfo -> info.data
                is UploadInfo.LazyTexture2DUploadInfo -> info.data.firstOrNull()?.dataProvider?.invoke()
            }

            if(data != null) {
                if (info.dataCompressed) {
                    GL13.glCompressedTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        info.dimension.width,
                        info.dimension.height,
                        info.internalFormat.glValue,
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
            uploadState = UploadState.Uploaded
        }
    }
}
