package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import ddsutil.DDSUtil
import ddsutil.ImageRescaler
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import isCompressed
import isSRGBA
import jogl.DDSImage
import org.lwjgl.BufferUtils
import java.awt.Color
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.max

sealed interface Texture {
    val description: TextureDescription
    val dimension: TextureDimension get() = description.dimension
    val id: Int
    val target: TextureTarget
    val internalFormat: InternalTextureFormat get() = description.internalFormat
    val handle: Long
    val textureFilterConfig: TextureFilterConfig get() = description.textureFilterConfig
    val wrapMode: WrapMode get() = description.wrapMode
    val srgba: Boolean get() = description.internalFormat.isSRGBA
}

interface Texture2D: Texture {
    override val description: Texture2DDescription
    override val dimension: TextureDimension2D get() = description.dimension
}
interface Texture3D: Texture {
    override val description: TextureDescription.Texture3DDescription
    override val dimension: TextureDimension3D get() = description.dimension
}
interface Texture2DArray: Texture3D
interface CubeMap: Texture {
    override val description: TextureDescription.CubeMapDescription
    override val dimension: TextureDimension2D get() = description.dimension
}
interface CubeMapArray: Texture {
    override val description: TextureDescription.CubeMapArrayDescription
    override val dimension: TextureDimension3D get() = description.dimension
}

val Texture.isMipMapped: Boolean get() = textureFilterConfig.minFilter.isMipMapped
val Texture.mipMapCount: Int get() = if(isMipMapped) dimension.mipMapCount else 0
val Texture.imageCount: Int get() = mipMapCount + 1

sealed interface TextureHandle<T: Texture> {
    val texture: T?
    var uploadState: UploadState
    var currentMipMapBias: Float // TODO: Make this a proper type and restrict range
}
interface StaticHandle<T: Texture>: TextureHandle<T> {
    override val texture: T
}
class StaticHandleImpl<T: Texture>(override val texture: T,
                                   override var uploadState: UploadState,
                                   override var currentMipMapBias: Float
): StaticHandle<T>
interface DynamicHandle<T: Texture>: TextureHandle<T> {
    override var texture: T?
    var fallback: StaticHandle<T>?
}

fun FileBasedTexture2D(
    path: String,
    file: File,
    texture: Texture2D?,
    unloadable: Boolean,
    description: Texture2DDescription,
): FileBasedTexture2D = if(unloadable) {
    DynamicFileBasedTexture2D(path, file, texture, null, description, UploadState.Unloaded)
} else {
    StaticFileBasedTexture2D(path, file, texture!!, description, UploadState.Unloaded)
}

sealed interface FileBasedTexture2D{
    val texture: Texture2D?
    fun getData(): List<ImageData>
    var uploadState: UploadState
    var currentMipMapBias: Float
    val handle: TextureHandle<Texture2D> get() = when(this) {
        is DynamicFileBasedTexture2D -> this
        is StaticFileBasedTexture2D -> this
    }
}

class StaticFileBasedTexture2D(
    val path: String,
    val file: File,
    override val texture: Texture2D,
    val description: Texture2DDescription,
    override var uploadState: UploadState,
    override var currentMipMapBias: Float = mipMapBiasForUploadState(uploadState, description.dimension)
): StaticHandle<Texture2D>, FileBasedTexture2D {
    private val bufferedImage: BufferedImage get() = ImageIO.read(file) ?: throw IllegalStateException("Cannot load $file")
    private val ddsImage: DDSImage get() = DDSImage.read(file)
    override fun getData(): List<ImageData> {
        val imageData = if (file.extension == "dds") {
            ddsImage.allMipMaps.mapIndexed { index, it ->
                if (description.internalFormat.isCompressed) {
                    ImageData(it.width, it.height, index) { it.data }
                } else {
                    ImageData(it.width, it.height, index) {
                        val decompressed = DDSUtil.decompressTexture(
                            ddsImage.getMipMap(0).data,
                            ddsImage.width,
                            ddsImage.height,
                            ddsImage.compressionFormat
                        ).apply {
                            rescaleToNextPowerOfTwo()
                        }
                        decompressed.toByteBuffer()
                    }
                }
            }
        } else {
            val precalculateMipMapsOnCpu = true
            if (precalculateMipMapsOnCpu) {
                val mipMapSizes = calculateMipMapSizes(description.dimension.width, description.dimension.height)
                (listOf(file) + precalculateMipMapFilesIfNecessary(
                    file,
                    description.dimension
                )).mapIndexed { index, file ->
                    ImageData(mipMapSizes[index].width, mipMapSizes[index].height, index) {
                        ImageIO.read(file).toByteBuffer()
                    }
                }
            } else {
                listOf(createSingleMipLevelTexture2DUploadInfo(bufferedImage))
            }
        }
        return if(description.textureFilterConfig.minFilter.isMipMapped) imageData else listOf(imageData.first())
    }
}
// TODO: Eliminate duplication
class DynamicFileBasedTexture2D(
    val path: String,
    val file: File,
    override var texture: Texture2D?,
    override var fallback: StaticHandle<Texture2D>?,
    val description: Texture2DDescription,
    override var uploadState: UploadState,
    override var currentMipMapBias: Float = mipMapBiasForUploadState(uploadState, description.dimension)
): DynamicHandle<Texture2D>, FileBasedTexture2D {
    private val bufferedImage: BufferedImage get() = ImageIO.read(file) ?: throw IllegalStateException("Cannot load $file")
    private val ddsImage: DDSImage get() = DDSImage.read(file)

    private val mipMapSizes = calculateMipMapSizes(description.dimension.width, description.dimension.height)
    private val mipMapFiles = getFileAndMipMapFiles(file, mipMapSizes.size).subList(1, mipMapSizes.size)
    private val files = listOf(file) + mipMapFiles
    private val allMipsPrecalculated = mipMapFiles.all { it.exists() }

    override fun getData(): List<ImageData> {
        val imageData = if (file.extension == "dds") {
            ddsImage.allMipMaps.mapIndexed { index, it -> // TODO: Does it contain all images or all mipmaps?
                if (description.internalFormat.isCompressed) {
                    ImageData(it.width, it.height, index) { it.data }
                } else {
                    ImageData(it.width, it.height, index) {
                        val decompressed = DDSUtil.decompressTexture(
                            ddsImage.getMipMap(0).data,
                            ddsImage.width,
                            ddsImage.height,
                            ddsImage.compressionFormat
                        ).apply {
                            rescaleToNextPowerOfTwo()
                        }
                        decompressed.toByteBuffer()
                    }
                }
            }
        } else {
            val precalculateMipMapsOnCpu = true
            if (precalculateMipMapsOnCpu) {
                if (allMipsPrecalculated) {
                    files.mapIndexed { index, file ->
                        ImageData(mipMapSizes[index].width, mipMapSizes[index].height, index) {
                            ImageIO.read(file).toByteBuffer()
                        }
                    }
                } else {
                    val mipMapSizes = calculateMipMapSizes(description.dimension.width, description.dimension.height)
                    actuallyCalculateMipMapFiles(mipMapSizes, bufferedImage, file, mipMapFiles)

                    files.mapIndexed { index, file ->
                        ImageData(mipMapSizes[index].width, mipMapSizes[index].height, index) {
                            ImageIO.read(file).toByteBuffer()
                        }
                    }
                }
            } else {
                listOf(createSingleMipLevelTexture2DUploadInfo(bufferedImage))
            }
        }
        return if(description.textureFilterConfig.minFilter.isMipMapped) imageData else listOf(imageData.first())
    }
}

fun getFileAndMipMapFiles(file: File, mipMapCount: Int) = (0..mipMapCount).map { mipLevel ->
    val replacement = if (mipLevel == 0) ".${file.extension}" else "$mipLevel.${file.extension}"
    File(file.absolutePath.replace(".${file.extension}", replacement))
}

fun mipMapBiasForUploadState(
    uploadState: UploadState,
    dimension: TextureDimension
) = when (uploadState) {
    is UploadState.Unloaded -> dimension.mipMapCount.toFloat()
    UploadState.Uploaded -> 0f
    is UploadState.Uploading -> uploadState.mipMapLevel.toFloat()
    is UploadState.MarkedForUpload -> dimension.mipMapCount.toFloat()
    UploadState.ForceFallback -> dimension.mipMapCount.toFloat()
}

fun BufferedImage.toByteBuffer(): ByteBuffer {
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

fun BufferedImage.rescaleToNextPowerOfTwo(): BufferedImage {
    val oldWidth = width
    val nextPowerOfTwoWidth = getNextPowerOfTwo(oldWidth)
    val oldHeight = height
    val nextPowerOfTwoHeight = getNextPowerOfTwo(oldHeight)

    val needsScaling = oldWidth != nextPowerOfTwoWidth || oldHeight != nextPowerOfTwoHeight

    return if (needsScaling) {
        val maxOfWidthAndHeightPoT = max(nextPowerOfTwoWidth, nextPowerOfTwoHeight)
        ImageRescaler().rescaleBI(this, maxOfWidthAndHeightPoT, maxOfWidthAndHeightPoT)
    } else this
}

fun getNextPowerOfTwo(old: Int): Int {
    var ret = 2
    while (ret < old) {
        ret *= 2
    }
    return ret
}

// TODO: Is this of any value still?
fun convertImageData(bufferedImage: BufferedImage): ByteArray {
    val raster: WritableRaster
    val texImage: BufferedImage

    val width = bufferedImage.width
    val height = bufferedImage.height

    if (bufferedImage.colorModel.hasAlpha()) {
        raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 4, null)
        texImage = BufferedImage(alphaColorModel, raster, false, Hashtable<Any, Any>())
    } else {
        raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 3, null)
        texImage = BufferedImage(colorModel, raster, false, Hashtable<Any, Any>())
    }

    // copy the source image into the produced image
    val g = texImage.graphics
    g.color = Color(0f, 0f, 0f, 0f)
    g.fillRect(0, 0, width, height)
    g.drawImage(bufferedImage, 0, 0, null)

    return (texImage.raster.dataBuffer as DataBufferByte).data
}