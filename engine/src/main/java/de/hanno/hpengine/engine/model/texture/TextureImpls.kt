package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.model.texture.Texture2D.TextureUploadInfo.*
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


data class Texture3D(override val dimension: TextureDimension3D,
                     override val id: Int,
                     override val target: GlTextureTarget,
                     override val internalFormat: Int,
                     override var handle: Long,
                     override val textureFilterConfig: TextureFilterConfig,
                     override val wrapMode: Int,
                     override var uploadState: UploadState) : Texture<TextureDimension3D> {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension3D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int): Texture3D {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture3DUploadInfo(TextureDimension(dimension.width, dimension.height, dimension.depth)), GlTextureTarget.TEXTURE_3D, filterConfig, internalFormat, wrapMode)
            return Texture3D(dimension, textureId, GlTextureTarget.TEXTURE_3D, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

data class SimpleCubeMap(override val dimension: TextureDimension2D,
                         override val id: Int,
                         override val target: GlTextureTarget,
                         override val internalFormat: Int,
                         override var handle: Long,
                         override val textureFilterConfig: TextureFilterConfig,
                         override val wrapMode: Int,
                         override var uploadState: UploadState) : Texture<TextureDimension2D> {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension2D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int): SimpleCubeMap {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture2DUploadInfo(TextureDimension(dimension.width, dimension.height)), GlTextureTarget.TEXTURE_CUBE_MAP, filterConfig, internalFormat, wrapMode)
            return SimpleCubeMap(dimension, textureId, GlTextureTarget.TEXTURE_CUBE_MAP, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

fun allocateTexture(gpuContext: GpuContext<OpenGl>, info: Texture2D.TextureUploadInfo, textureTarget: GlTextureTarget, filterConfig: TextureFilterConfig = TextureFilterConfig(), internalFormat: Int, wrapMode: Int = GL12.GL_REPEAT): Triple<Int, Int, Long> {
    return gpuContext.calculate {
        val textureId = glGenTextures()
        val glTarget = textureTarget.glTarget
        glBindTexture(glTarget, textureId)
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_S, wrapMode)
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_T, wrapMode)
        glTexParameteri(glTarget, GL_TEXTURE_MIN_FILTER, filterConfig.minFilter.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_MAG_FILTER, filterConfig.magFilter.glValue)
//        TODO: Remove casts
        when(textureTarget) {
            GlTextureTarget.TEXTURE_2D -> {
                val info = info as Texture2DUploadInfo
                GL42.glTexStorage2D(glTarget, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height)
            }
            GlTextureTarget.TEXTURE_2D_ARRAY, GlTextureTarget.TEXTURE_3D -> {
                val info = info as Texture3DUploadInfo
                GL42.glTexStorage3D(glTarget, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height, info.dimension.depth)
                glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
            }
            GlTextureTarget.TEXTURE_CUBE_MAP -> {
                val info = info as Texture2DUploadInfo
                GL42.glTexStorage2D(GL40.GL_TEXTURE_CUBE_MAP, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height)
                glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
            }
            GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY -> {
                val info = info as Texture3DUploadInfo
                GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, info.dimension.getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height, info.dimension.depth * 6)
                glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
            }
        }
        GL30.glGenerateMipmap(glTarget)

        val handle = if(gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glGetTextureHandleARB(textureId) else -1
        Triple(textureId, internalFormat, handle)
    }
}

data class CubeMapArray(override val dimension: TextureDimension3D,
                        override val id: Int,
                        override val target: GlTextureTarget = GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY,
                        override val internalFormat: Int,
                        override var handle: Long,
                        override val textureFilterConfig: TextureFilterConfig,
                        override val wrapMode: Int,
                        override var uploadState: UploadState) : Texture<TextureDimension3D> {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension3D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int): CubeMapArray {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture3DUploadInfo(TextureDimension(dimension.width, dimension.height)), GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, filterConfig, internalFormat, wrapMode)
            return CubeMapArray(dimension, textureId, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

fun CubeMapArray.createViews(gpuContext: GpuContext<OpenGl>): List<SimpleCubeMap> {
    return (0 until dimension.depth).map { index ->
        createView(gpuContext, index)
    }
}
fun CubeMapArray.createView(gpuContext: GpuContext<OpenGl>, index: Int): SimpleCubeMap {
    val cubeMapView = gpuContext.genTextures()
    gpuContext.execute("SimpleCubeMapArray.createView") {
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id,
                internalFormat, 0, 1,
                6 * index, 6)
    }

    val cubeMapHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.calculate { ARBBindlessTexture.glGetTextureHandleARB(cubeMapView) }
        gpuContext.execute("SimpleCubeMapArray.createView.cubeMapHandle") { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return SimpleCubeMap(
        TextureDimension(dimension.width, dimension.height),
        cubeMapView,
        GlTextureTarget.TEXTURE_CUBE_MAP,
        internalFormat,
        cubeMapHandle,
        textureFilterConfig,
        wrapMode,
        UploadState.UPLOADED
    )
}

data class Texture2D(override val dimension: TextureDimension2D,
                     override val id: Int,
                     override val target: GlTextureTarget,
                     override val internalFormat: Int,
                     override var handle: Long,
                     override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
                     override val wrapMode: Int,
                     override var uploadState: UploadState) : Texture<TextureDimension2D> {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, file: File, path: String, srgba: Boolean = false): Texture2D {
            val fileAsDds = File(path.split(".")[0] + ".dds")
            if(!file.exists() || !file.isFile) {
                throw IllegalStateException("Cannot load file $file as texture as it doesn't exist")
            }
            return if(file.extension == "dds") {
                val ddsImage = DDSImage.read(file)
                val compressedTextures = true
                if(compressedTextures) {
                    val image = Texture2DUploadInfo(TextureDimension(ddsImage.width, ddsImage.height), ddsImage.getMipMap(0).data, dataCompressed = true, srgba = srgba)
                    Texture2D(gpuContext, Texture2DUploadInfo(TextureDimension(ddsImage.width, ddsImage.height), ddsImage.getMipMap(0).data, dataCompressed = true, srgba = srgba))
                } else {
                    Texture2D(gpuContext, DDSUtil.decompressTexture(ddsImage.getMipMap(0).data, ddsImage.width, ddsImage.height, ddsImage.compressionFormat).apply {
                        with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() }
                    })
                }
            } else Texture2D(gpuContext, ImageIO.read(file).apply { with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() } }, srgba)

        }
        operator fun invoke(gpuContext: GpuContext<OpenGl>, image: BufferedImage, srgba: Boolean = false): Texture2D {
            val image1 = Texture2DUploadInfo(TextureDimension(image.width, image.height), image.toByteBuffer(), srgba = srgba)
            return Texture2D(gpuContext, Texture2DUploadInfo(TextureDimension(image.width, image.height), image.toByteBuffer(), srgba = srgba), internalFormat = if (image1.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT)
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

        operator fun invoke(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo, textureFilterConfig: TextureFilterConfig = TextureFilterConfig(), internalFormat: Int = if (info.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT): Texture2D {
            return gpuContext.calculate {
                val (textureId, internalFormat, handle) = allocateTexture(gpuContext, info, GlTextureTarget.TEXTURE_2D, TextureFilterConfig(), internalFormat, GL12.GL_REPEAT)
                if(gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                Texture2D(dimension = info.dimension,
                    id = textureId,
                    target = GlTextureTarget.TEXTURE_2D,
                    textureFilterConfig = textureFilterConfig,
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

        private fun Texture2D.upload(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo, internalFormat: Int) {
            val usePbo = true
            if(usePbo) {
                uploadWithPixelBuffer(gpuContext, info, internalFormat)
            } else {
                uploadWithoutPixelBuffer(gpuContext, info, internalFormat)
            }
        }
        private fun Texture2D.uploadWithPixelBuffer(gpuContext: GpuContext<OpenGl>, info: Texture2DUploadInfo, internalFormat: Int) {
            if(info.buffer == null) return

            val pbo = gpuContext.calculate {
                PixelBufferObject()
            }
            pbo.put(gpuContext, info.buffer)
            gpuContext.execute("SimpleTexture2D.uploadWithPixelBuffer") {
                pbo.unmap(gpuContext)
                pbo.bind()
                glBindTexture(GL_TEXTURE_2D, id)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, internalFormat, info.buffer.capacity(), 0)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
                gpuContext.getExceptionOnError("After glTexSubImage")
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                pbo.unbind()
                uploadState = UploadState.UPLOADED
            }
        }

        private fun Texture2D.uploadWithoutPixelBuffer(gpuContext: GpuContext<*>, info: Texture2DUploadInfo, internalFormat: Int) {
            gpuContext.execute("SimpleTexture2D.uploadWithoutPixelBuffer", {
                glBindTexture(GL_TEXTURE_2D, id)
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

    sealed class TextureUploadInfo {
        data class Texture2DUploadInfo(val dimension: TextureDimension2D, val buffer: ByteBuffer? = null, val dataCompressed: Boolean = false, val srgba: Boolean = false): TextureUploadInfo()
        data class Texture3DUploadInfo(val dimension: TextureDimension3D): TextureUploadInfo()
        data class CubeMapUploadInfo(val dimension: TextureDimension2D): TextureUploadInfo()
        data class CubeMapArrayUploadInfo(val dimension: TextureDimension3D): TextureUploadInfo()
    }
}

data class FileBasedSimpleTexture(val path: String, val backingTexture: Texture2D): Texture<TextureDimension2D> by backingTexture {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, directory: AbstractDirectory, srgba: Boolean = false): FileBasedSimpleTexture {
            return invoke(gpuContext, path, directory.resolve(path), srgba)
        }

        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, file: File, srgba: Boolean = false) =
                FileBasedSimpleTexture(path, Texture2D(gpuContext, file, path, srgba))
    }
}

