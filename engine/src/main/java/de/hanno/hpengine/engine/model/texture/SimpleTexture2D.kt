package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.model.texture.SimpleTexture2D.Companion.TextureUploadInfo.*
import jogl.DDSImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL13.glCompressedTexSubImage2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

data class SimpleTexture3D(override val dimension: TextureDimension3D,
                           override val textureId: Int,
                           override val target: GlTextureTarget,
                           override val internalFormat: Int,
                           override var handle: Long,
                           override val textureFilterConfig: TextureFilterConfig,
                           override val wrapMode: Int,
                           override var uploadState: UploadState) : Texture<TextureDimension3D> {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension3D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int): SimpleTexture3D {
            val (textureId, internalFormat, handle) = SimpleTexture2D.allocateTexture(gpuContext, Texture3DUploadInfo(TextureDimension(dimension.width, dimension.height, dimension.depth)), GL12.GL_TEXTURE_3D, filterConfig, internalFormat, wrapMode)
            return SimpleTexture3D(dimension, textureId, GlTextureTarget.TEXTURE_3D, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

data class SimpleTexture2D(override val dimension: TextureDimension2D,
                           override val textureId: Int,
                           override val target: GlTextureTarget,
                           override val internalFormat: Int,
                           override var handle: Long,
                           override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
                           override val wrapMode: Int,
                           override var uploadState: UploadState) : Texture<TextureDimension2D> {

    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, directory: AbstractDirectory = Config.getInstance().directoryManager.gameDir, srgba: Boolean = false): SimpleTexture2D {
            val fileAsDds = File(path.split(".")[0] + ".dds")
            val file = /*if(fileAsDds.exists()) fileAsDds else*/ directory.resolve(path)
            if(!file.exists() || !file.isFile) {
                throw IllegalStateException("Cannot load file $file as texture as it doesn't exist")
            }
            return if(file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if(compressedTextures) {
                    SimpleTexture2D(gpuContext, Texture2DUploadInfo(TextureDimension(ddsImage.width, ddsImage.height), ddsImage.getMipMap(0).data, dataCompressed = true, srgba = srgba))
                } else {
                    SimpleTexture2D(gpuContext, DDSUtil.decompressTexture(ddsImage.getMipMap(0).data, ddsImage.width, ddsImage.height, ddsImage.compressionFormat).apply {
                        with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() }
                    })
                }
            } else SimpleTexture2D(gpuContext, ImageIO.read(file).apply { with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() } }, srgba)

        }
        operator fun invoke(gpuContext: GpuContext<OpenGl>, image: BufferedImage, srgba: Boolean = false): SimpleTexture2D {
            return SimpleTexture2D(gpuContext, Texture2DUploadInfo(TextureDimension(image.width, image.height), image.toByteBuffer(), srgba = srgba))
        }

        sealed class TextureUploadInfo {
            data class Texture2DUploadInfo(val dimension: TextureDimension2D, val buffer: ByteBuffer, val dataCompressed: Boolean = false, val srgba: Boolean = false): TextureUploadInfo()
            data class Texture3DUploadInfo(val dimension: TextureDimension3D): TextureUploadInfo()
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

        operator fun invoke(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo): SimpleTexture2D {
            return gpuContext.calculate {
                val (textureId, internalFormat, handle) = allocateTexture(gpuContext, info, GL_TEXTURE_2D, TextureFilterConfig(), if (info.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, GL12.GL_REPEAT)
                if(gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                SimpleTexture2D(dimension = info.dimension,
                    textureId = textureId,
                    target = GlTextureTarget.TEXTURE_2D,
                    internalFormat = internalFormat,
                    handle = handle,
                    wrapMode = GL12.GL_CLAMP_TO_EDGE,
                    uploadState = UploadState.NOT_UPLOADED).apply {
                        CompletableFuture.supplyAsync {
                            this@apply.upload(gpuContext, info, internalFormat)
                        }
                }
            }
        }

        fun allocateTexture(gpuContext: GpuContext<OpenGl>, info: TextureUploadInfo, textureTarget: Int, filterConfig: TextureFilterConfig = TextureFilterConfig(), internalFormat: Int, wrapMode: Int): Triple<Int, Int, Long> {
            return gpuContext.calculate {
                val textureId = glGenTextures()
                glBindTexture(textureTarget, textureId)
                glTexParameteri(textureTarget, GL_TEXTURE_WRAP_S, wrapMode)
                glTexParameteri(textureTarget, GL_TEXTURE_WRAP_T, wrapMode)
                glTexParameteri(textureTarget, GL_TEXTURE_MIN_FILTER, filterConfig.minFilter.glValue)
                glTexParameteri(textureTarget, GL_TEXTURE_MAG_FILTER, filterConfig.magFilter.glValue)
                when(info) {
                    is Texture2DUploadInfo -> GL42.glTexStorage2D(textureTarget, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height)
                    is Texture3DUploadInfo -> {
                        GL42.glTexStorage3D(textureTarget, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height, info.dimension.depth)
                        glTexParameteri(textureTarget, GL_TEXTURE_WRAP_R, wrapMode)
                    }
                }
                GL30.glGenerateMipmap(textureTarget)

                val handle = if(gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glGetTextureHandleARB(textureId) else -1
                Triple(textureId, internalFormat, handle)
            }
        }

        private fun SimpleTexture2D.upload(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo, internalFormat: Int) {
            val usePbo = true
            if(usePbo) {
                uploadWithPixelBuffer(gpuContext, info, internalFormat)
            } else {
                uploadWithoutPixelBuffer(gpuContext, info, internalFormat)
            }
        }
        private fun SimpleTexture2D.uploadWithPixelBuffer(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo, internalFormat: Int) {
            val pbo = gpuContext.calculate {
                PixelBufferObject()
            }
            pbo.put(gpuContext, info.buffer)
            gpuContext.execute {
                pbo.bind()
                glBindTexture(GL_TEXTURE_2D, textureId)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, internalFormat, info.buffer.capacity(), 0)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                pbo.unbind()
                uploadState = UploadState.UPLOADED
            }
        }

        private fun SimpleTexture2D.uploadWithoutPixelBuffer(gpuContext: GpuContext<*>, info: Texture2DUploadInfo, internalFormat: Int) {
            gpuContext.execute({
                glBindTexture(GL_TEXTURE_2D, textureId)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, internalFormat, info.buffer)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, info.buffer)
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                uploadState = UploadState.UPLOADED
            }, false)
        }
    }
}

data class FileBasedSimpleTexture(val path: String, val backingTexture: SimpleTexture2D): Texture<TextureDimension2D> by backingTexture {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, directory: AbstractDirectory, srgba: Boolean = false): FileBasedSimpleTexture {
            return FileBasedSimpleTexture(path, SimpleTexture2D(gpuContext, path, directory, srgba))
        }
    }
}


