package de.hanno.hpengine.model.texture

import ddsutil.DDSUtil
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.model.texture.Texture2D.TextureUploadInfo.*
import de.hanno.hpengine.model.texture.TextureManager.Companion.convertCubeMapData
import de.hanno.hpengine.model.texture.TextureManager.Companion.glAlphaColorModel
import de.hanno.hpengine.model.texture.TextureManager.Companion.glColorModel
import jogl.DDSImage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_BGR
import org.lwjgl.opengl.GL12.GL_BGRA
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL13.glCompressedTexSubImage2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_3BYTE_BGR
import java.awt.image.BufferedImage.TYPE_4BYTE_ABGR
import java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE
import java.awt.image.BufferedImage.TYPE_INT_BGR
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO


data class Texture3D(override val dimension: TextureDimension3D,
                     override val id: Int,
                     override val target: GlTextureTarget,
                     override val internalFormat: Int,
                     override var handle: Long,
                     override val textureFilterConfig: TextureFilterConfig,
                     override val wrapMode: Int,
                     override var uploadState: UploadState
) : Texture {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension3D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int = GL_REPEAT): Texture3D {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture3DUploadInfo(dimension), GlTextureTarget.TEXTURE_3D, filterConfig, internalFormat, wrapMode)
            return Texture3D(dimension, textureId, GlTextureTarget.TEXTURE_3D, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

data class CubeMap(override val dimension: TextureDimension2D,
                   override val id: Int,
                   override val target: GlTextureTarget,
                   override val internalFormat: Int,
                   override var handle: Long,
                   override val textureFilterConfig: TextureFilterConfig,
                   override val wrapMode: Int,
                   override var uploadState: UploadState
) : ICubeMap {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension2D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int = GL_REPEAT): CubeMap {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture2DUploadInfo(dimension), GlTextureTarget.TEXTURE_CUBE_MAP, filterConfig, internalFormat, wrapMode)
            return CubeMap(dimension, textureId, GlTextureTarget.TEXTURE_CUBE_MAP, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

enum class BackingFileMode {
    Single,
    Six
}
data class FileBasedCubeMap(val path: String, val backingTexture: ICubeMap, val files: List<File>, val isBgrFormat: Boolean = false): ICubeMap by backingTexture {
    init {
        require(files.isNotEmpty()) { "Cannot create CubeMap without any files!" }
        require(files.size == 1 || files.size == 6) { "Pass either 1 or 6 images to create a CubeMap!" }
    }
    val backingFileMode = if(files.size == 1) BackingFileMode.Single else BackingFileMode.Six
    val file = files.first()
    val bufferedImage: BufferedImage = ImageIO.read(file)
    val srcPixelFormat = if (bufferedImage.colorModel.hasAlpha()) {
        if (isBgrFormat) GL_BGRA else GL_RGBA
    } else {
        if(isBgrFormat) GL_BGR else GL_RGB
    }
    val width = bufferedImage.width
    val height = bufferedImage.height
    val tileDimension = if(backingFileMode == BackingFileMode.Six) { TextureDimension(width, height) } else TextureDimension(width / 4, height / 3)

    constructor(path: String, backingTexture: ICubeMap, vararg file: File): this(path, backingTexture, file.toList())

    fun load(gpuContext: GpuContext<*>) = CompletableFuture.supplyAsync {
        if(backingFileMode == BackingFileMode.Six) {
            GlobalScope.launch {
                files.map {
                    GlobalScope.async {
                        ImageIO.read(it)
                    }
                }.awaitAll().let { upload(gpuContext, it.getCubeMapUploadInfo()) }
            }
        } else {
            val bufferedImage: BufferedImage = ImageIO.read(file)
            upload(gpuContext, bufferedImage.getCubeMapUploadInfo())
        }
    }

    fun upload(gpuContext: GpuContext<*>, cubeMapFaceTarget: Int, buffer: ByteBuffer) = gpuContext.invoke {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
        glTexSubImage2D(cubeMapFaceTarget,
                0,
                0,
                0,
                tileDimension.width,
                tileDimension.height,
                srcPixelFormat,
                GL_UNSIGNED_BYTE,
                buffer)
    }

    fun upload(gpuContext: GpuContext<*>, info: CubeMapUploadInfo) = gpuContext.invoke {
        gpuContext.bindTexture(this)

        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, info.buffers[0])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, info.buffers[1])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, info.buffers[2])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, info.buffers[3])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, info.buffers[4])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, info.buffers[5])

        gpuContext.invoke {
            GL30.glGenerateMipmap(GlTextureTarget.TEXTURE_CUBE_MAP.glTarget)
        }
    }

    companion object {

        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, files: List<File>, srgba: Boolean = false): FileBasedCubeMap {
            val internalFormat: Int = if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT

            val bufferedImage: BufferedImage = ImageIO.read(files.first())
            val width = bufferedImage.width
            val height = bufferedImage.height
            val tileDimension = TextureDimension(width, height)

            val isBgrFormat = bufferedImage.isBgrFormat

            val fileBasedCubeMap = FileBasedCubeMap(path, CubeMap(gpuContext, tileDimension, TextureFilterConfig(), internalFormat, GL_REPEAT), files, isBgrFormat)

            return fileBasedCubeMap.apply {
                load(gpuContext)
            }
        }

        private val BufferedImage.isBgrFormat: Boolean
            get() = type == TYPE_INT_BGR || type == TYPE_3BYTE_BGR || type == TYPE_4BYTE_ABGR || type == TYPE_4BYTE_ABGR_PRE

        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, file: File, srgba: Boolean = false): FileBasedCubeMap {
            val internalFormat: Int = if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT

            val bufferedImage: BufferedImage = ImageIO.read(file)
            val width = bufferedImage.width
            val height = bufferedImage.height
            val tileDimension = TextureDimension(width / 4, height / 3)

            val fileBasedCubeMap = FileBasedCubeMap(path, CubeMap(gpuContext, tileDimension, TextureFilterConfig(), internalFormat, GL_REPEAT), file)

            return fileBasedCubeMap.apply {
                load(gpuContext)
            }
        }
    }
}
private fun List<BufferedImage>.getCubeMapUploadInfo(): CubeMapUploadInfo {
    return CubeMapUploadInfo(TextureDimension(first().width, first().height), convertCubeMapData(this).map<ByteArray, ByteBuffer> { byteArray ->
        ByteBuffer.allocateDirect(byteArray.size).apply {
            buffer(byteArray)
        }
    })
}
private fun BufferedImage.getCubeMapUploadInfo(): CubeMapUploadInfo {
    val width = width
    val height = height
    val tileDimension = TextureDimension(width / 4, height / 3)
    val data = convertCubeMapData(this, width, height, glAlphaColorModel, glColorModel)

    return CubeMapUploadInfo(tileDimension, data.map<ByteArray, ByteBuffer> { byteArray ->
        ByteBuffer.allocateDirect(byteArray.size).apply {
            buffer(byteArray)
        }
    })
}

fun allocateTexture(gpuContext: GpuContext<OpenGl>, info: Texture2D.TextureUploadInfo, textureTarget: GlTextureTarget, filterConfig: TextureFilterConfig = TextureFilterConfig(), internalFormat: Int, wrapMode: Int = GL12.GL_REPEAT): Triple<Int, Int, Long> {
    info.validate()
    return gpuContext.invoke {
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
                GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, TextureDimension2D(info.dimension.width, info.dimension.height).getMipMapCount(), internalFormat, info.dimension.width, info.dimension.height, info.dimension.depth * 6)
                glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
            }
        }
        if(filterConfig.minFilter.isMipMapped) {
            GL30.glGenerateMipmap(glTarget)
        }

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
                        override var uploadState: UploadState
) : Texture {

    val size: Int
        get() = dimension.depth
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, dimension: TextureDimension3D, filterConfig: TextureFilterConfig, internalFormat: Int, wrapMode: Int): CubeMapArray {
            val (textureId, internalFormat, handle) = allocateTexture(gpuContext, Texture3DUploadInfo(dimension), GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, filterConfig, internalFormat, wrapMode)
            return CubeMapArray(dimension, textureId, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, internalFormat, handle, filterConfig, wrapMode, UploadState.UPLOADED)
        }
    }
}

fun CubeMapArray.createViews(gpuContext: GpuContext<OpenGl>): List<CubeMap> {
    return (0 until dimension.depth).map { index ->
        createView(gpuContext, index)
    }
}
fun CubeMapArray.createView(gpuContext: GpuContext<OpenGl>, index: Int): CubeMap {
    val cubeMapView = gpuContext.genTextures()
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }

    gpuContext.invoke {
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id,
                internalFormat, 0, mipmapCount-1,
                6 * index, 6)
    }

    val cubeMapHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return CubeMap(
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
fun CubeMapArray.createView(gpuContext: GpuContext<OpenGl>, index: Int, faceIndex: Int): Texture2D {
    val cubeMapFaceView = gpuContext.genTextures()
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }
    gpuContext.invoke {
        GL43.glTextureView(cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
                internalFormat, 0, 1,
                6 * index + faceIndex, 1)
    }

    val cubeMapFaceHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return Texture2D(
            TextureDimension(dimension.width, dimension.height),
            cubeMapFaceView,
            GlTextureTarget.TEXTURE_2D,
            internalFormat,
            cubeMapFaceHandle,
            textureFilterConfig,
            wrapMode,
            UploadState.UPLOADED
    )
}
fun CubeMap.createView(gpuContext: GpuContext<OpenGl>, faceIndex: Int): Texture2D {
    val cubeMapFaceView = gpuContext.genTextures()
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }
    gpuContext.invoke {
        GL43.glTextureView(cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
                internalFormat, 0, 1,
                faceIndex, 1)
    }

    val cubeMapFaceHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return Texture2D(
            TextureDimension(dimension.width, dimension.height),
            cubeMapFaceView,
            GlTextureTarget.TEXTURE_2D,
            internalFormat,
            cubeMapFaceHandle,
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
                     override var uploadState: UploadState
) : Texture {
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
                    Texture2D(gpuContext, image)
                } else {
                    Texture2D(gpuContext, DDSUtil.decompressTexture(ddsImage.getMipMap(0).data, ddsImage.width, ddsImage.height, ddsImage.compressionFormat).apply {
                        with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() }
                    })
                }
            } else Texture2D(gpuContext, ImageIO.read(file).apply { with(DDSConverter) { this@apply.rescaleToNextPowerOfTwo() } }, srgba)

        }
        operator fun invoke(gpuContext: GpuContext<OpenGl>, image: BufferedImage, srgba: Boolean = false): Texture2D {
            val buffer = image.toByteBuffer()
            val image1 = Texture2DUploadInfo(TextureDimension(image.width, image.height), buffer, srgba = srgba)
            return Texture2D(gpuContext, Texture2DUploadInfo(TextureDimension(image.width, image.height), buffer, srgba = srgba), internalFormat = if (image1.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT)
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
            gpuContext: GpuContext<OpenGl>,
            info: Texture2DUploadInfo,
            textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
            internalFormat: Int = if (info.srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT,
            wrapMode: Int = GL12.GL_REPEAT
        ): Texture2D {
            return gpuContext.invoke {
                val (textureId, internalFormat, handle) = allocateTexture(gpuContext, info, GlTextureTarget.TEXTURE_2D, textureFilterConfig, internalFormat, wrapMode)
                if(gpuContext.isSupported(BindlessTextures)) ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
                Texture2D(dimension = info.dimension,
                    id = textureId,
                    target = GlTextureTarget.TEXTURE_2D,
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

            val pbo = gpuContext.invoke {
                PixelBufferObject()
            }
            pbo.put(gpuContext, info.buffer)
            gpuContext.invoke {
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
            gpuContext.invoke {
                glBindTexture(GL_TEXTURE_2D, id)
                if (info.dataCompressed) {
                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, internalFormat, info.buffer)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, info.buffer)
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D)
                uploadState = UploadState.UPLOADED
            }
        }
    }

    sealed class TextureUploadInfo {
        data class Texture2DUploadInfo(val dimension: TextureDimension2D, val buffer: ByteBuffer? = null, val dataCompressed: Boolean = false, val srgba: Boolean = false): TextureUploadInfo()
        data class Texture3DUploadInfo(val dimension: TextureDimension3D): TextureUploadInfo()
        data class CubeMapUploadInfo(val dimension: TextureDimension2D, val buffers: List<ByteBuffer> = emptyList()): TextureUploadInfo()
        data class CubeMapArrayUploadInfo(val dimension: TextureDimension3D): TextureUploadInfo()
    }
}
fun Texture2D.TextureUploadInfo.validate(): Unit = when(this) {
    is Texture2DUploadInfo -> {
        require(dimension.width > 0) { "Illegal width $dimension" }
        require(dimension.height > 0) { "Illegal height $dimension" }
    }
    is Texture3DUploadInfo -> {
        require(dimension.width > 0) { "Illegal width $dimension" }
        require(dimension.height > 0) { "Illegal height $dimension" }
        require(dimension.depth > 0) { "Illegal depth $dimension" }
    }
    is CubeMapUploadInfo -> {
        require(dimension.width > 0) { "Illegal width $dimension" }
        require(dimension.height > 0) { "Illegal height $dimension" }
    }
    is CubeMapArrayUploadInfo -> {
        require(dimension.width > 0) { "Illegal width $dimension" }
        require(dimension.height > 0) { "Illegal height $dimension" }
        require(dimension.depth > 0) { "Illegal depth $dimension" }
    }
}.let {}

data class FileBasedTexture2D(val path: String, val file: File, val backingTexture: Texture2D): Texture by backingTexture {
    companion object {
        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, directory: AbstractDirectory, srgba: Boolean = false): FileBasedTexture2D {
            return invoke(gpuContext, path, directory.resolve(path), srgba)
        }

        operator fun invoke(gpuContext: GpuContext<OpenGl>, path: String, file: File, srgba: Boolean = false) =
                FileBasedTexture2D(path, file, Texture2D(gpuContext, file, path, srgba))
    }
}

fun ByteBuffer.buffer(values: ByteArray) {
    order(ByteOrder.nativeOrder())
    put(values, 0, values.size)
    flip()
}