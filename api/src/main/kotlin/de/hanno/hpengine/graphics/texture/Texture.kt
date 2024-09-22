package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import ddsutil.ImageRescaler
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode
import org.lwjgl.BufferUtils
import java.awt.Color
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max

sealed interface Texture {
    val dimension: TextureDimension
    val id: Int
    val target: TextureTarget
    val internalFormat: InternalTextureFormat
    val handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: WrapMode
    var uploadState: UploadState
    var currentMipMapBias: Float
    val srgba: Boolean get() = false
    val unloadable: Boolean
}

interface Texture2D: Texture {
    override val dimension: TextureDimension2D
}
interface Texture3D: Texture {
    override val dimension: TextureDimension3D
}
interface Texture2DArray: Texture3D
interface CubeMap: Texture {
    override val dimension: TextureDimension2D
}
interface CubeMapArray: Texture {
    override val dimension: TextureDimension3D
}

val Texture.isMipMapped: Boolean get() = textureFilterConfig.minFilter.isMipMapped
val Texture.mipmapCount: Int get() = if(isMipMapped) { dimension.getMipMapCount() } else 0


data class FileBasedTexture2D<out T: Texture2D>(
    val path: String,
    val file: File,
    val backingTexture: T,
    override val unloadable: Boolean
) : Texture2D by backingTexture

fun getFileAndMipMapFiles(file: File, mipMapCount: Int) = (0..mipMapCount).map { mipLevel ->
    val replacement = if (mipLevel == 0) ".${file.extension}" else "$mipLevel.${file.extension}"
    File(file.absolutePath.replace(".${file.extension}", replacement))
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