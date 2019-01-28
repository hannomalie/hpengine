package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import jogl.DDSImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.glCompressedTexSubImage2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

data class SimpleTexture(override val dimension: TextureDimension2D, override val textureId: Int, override val target: GlTextureTarget, override val internalFormat: Int, override var handle: Long, override val textureFilterConfig: TextureFilterConfig, override val wrapMode: Int, override var uploadState: UploadState) : Texture<TextureDimension2D> {

    companion object {
        operator fun invoke(gpuContext: GpuContext, path: String, srgba: Boolean = false): SimpleTexture {
            val fileAsDds = File(path.split(".")[0] + ".dds")
            val file = /*if(fileAsDds.exists()) fileAsDds else*/ File(path)
            if(!file.exists() || !file.isFile) {
                throw IllegalStateException("Cannot load file $file as texture")
            }
            return if(file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if(compressedTextures) {
                    SimpleTexture(gpuContext, TextureUploadInfo(ddsImage.width, ddsImage.height, ddsImage.getMipMap(0).data, dataCompressed = true, srgba = srgba))
                } else {
                    SimpleTexture(gpuContext, DDSUtil.decompressTexture(ddsImage.getMipMap(0).data, ddsImage.width, ddsImage.height, ddsImage.compressionFormat).apply {
                        with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() }
                    })
                }
            } else SimpleTexture(gpuContext, ImageIO.read(file).apply { with(DDSConverter) {this@apply.rescaleToNextPowerOfTwo()} }, srgba)

        }
        operator fun invoke(gpuContext: GpuContext, image: BufferedImage, srgba: Boolean = false): SimpleTexture {
            return SimpleTexture(gpuContext, TextureUploadInfo(image.width, image.height, image.toByteBuffer(), srgba = srgba))
        }

        data class TextureUploadInfo(val width: Int, val height: Int, val buffer: ByteBuffer, val dataCompressed: Boolean = false, val srgba: Boolean = false)

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

        operator fun invoke(gpuContext: GpuContext, info: TextureUploadInfo): SimpleTexture {
            return gpuContext.calculate {
                val textureId = glGenTextures()
                glBindTexture(GL_TEXTURE_2D, textureId)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_REPEAT)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_REPEAT)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR_MIPMAP_LINEAR)
                val internalFormat = if (info.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT//GL_RGBA8
                GL42.glTexStorage2D(GL_TEXTURE_2D, 1, internalFormat, info.width, info.height)
                GL30.glGenerateMipmap(GL_TEXTURE_2D)

                val handle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
                ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                SimpleTexture(TextureDimension(info.width, info.height), textureId, GlTextureTarget.TEXTURE_2D, internalFormat, handle, TextureFilterConfig(TextureFilterConfig.MinFilter.LINEAR, TextureFilterConfig.MagFilter.LINEAR), GL12.GL_CLAMP_TO_EDGE, UploadState.NOT_UPLOADED).apply {
                    CompletableFuture.supplyAsync {
                        this@apply.upload(gpuContext, info, internalFormat)
                    }
                }
            }
        }

        private fun SimpleTexture.upload(gpuContext: GpuContext, info: TextureUploadInfo, internalFormat: Int) {
            val usePbo = true
            if(usePbo) {
                uploadWithPixelBuffer(gpuContext, info, internalFormat)
            } else {
                uploadWithoutPixelBuffer(gpuContext, info, internalFormat)
            }
        }
        private fun SimpleTexture.uploadWithPixelBuffer(gpuContext: GpuContext, info: TextureUploadInfo, internalFormat: Int) {
            val pbo = gpuContext.calculate {
                PixelBufferObject()
            }
            pbo.put(gpuContext, info.buffer)
            gpuContext.execute {
                pbo.bind()
                glBindTexture(GL_TEXTURE_2D, textureId)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.width, info.height, internalFormat, info.buffer.capacity(), 0)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.width, info.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                pbo.unbind()
                uploadState = UploadState.UPLOADED
            }
        }

        private fun SimpleTexture.uploadWithoutPixelBuffer(gpuContext: GpuContext, info: TextureUploadInfo, internalFormat: Int) {
            gpuContext.execute({
                glBindTexture(GL_TEXTURE_2D, textureId)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.width, info.height, internalFormat, info.buffer)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.width, info.height, GL_RGBA, GL_UNSIGNED_BYTE, info.buffer)
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                uploadState = UploadState.UPLOADED
            }, false)
        }
    }
}

data class FileBasedSimpleTexture(val path: String, val backingTexture: SimpleTexture): Texture<TextureDimension2D> by backingTexture {
    companion object {
        operator fun invoke(gpuContext: GpuContext, path: String, srgba: Boolean = false): FileBasedSimpleTexture {
            return FileBasedSimpleTexture(path, SimpleTexture(gpuContext, path, srgba))
        }
    }
}


