package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import bitsPerPixel
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.logger
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt


sealed interface UploadState {
    data object Unloaded : UploadState
    data class Uploading(val mipMapLevel: Int): UploadState
    data object MarkedForUpload: UploadState
    data object Uploaded : UploadState
    data object ForceFallback: UploadState
}
// https://stackoverflow.com/questions/9417356/bufferedimage-resize
fun BufferedImage.resize(targetSize: Int): BufferedImage {
    require (targetSize > 0) { "Don't pass in 0 targetSize" }

    var targetWidth = targetSize
    var targetHeight = targetSize
    val ratio = height.toFloat() / width.toFloat()
    if (ratio <= 1) { //square or landscape-oriented image
        targetHeight = ceil((targetWidth.toFloat() * ratio).toDouble()).toInt()
    } else { //portrait image
        targetWidth = (targetHeight.toFloat() / ratio).roundToInt()
    }
    return BufferedImage(
        maxOf(1, targetWidth),
        maxOf(1, targetHeight),
        if (transparency == Transparency.OPAQUE) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
    ).apply {
        createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
            )
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            drawImage(this@resize, 0, 0, targetWidth, targetHeight, null)
            dispose()
        }
    }
}

typealias Texture2DArrayDescription = TextureDescription.Texture3DDescription

sealed interface TextureDescription {
    val dimension: TextureDimension
    val internalFormat: InternalTextureFormat
    val textureFilterConfig: TextureFilterConfig
    val mipMapCount: Int get() = if(textureFilterConfig.minFilter.isMipMapped) dimension.mipMapCount else 1
    val imageCount: Int get() = mipMapCount + 1
    val wrapMode: WrapMode

    val pixelCount: Int get() = when(val dimension = dimension) {
        is TextureDimension1D -> dimension.width
        is TextureDimension2D -> dimension.width * dimension.height
        is TextureDimension3D -> dimension.width * dimension.height * dimension.depth
    }
    val gpuSizeInBytes: SizeInBytes get() {
        val mipMapFactor = if(textureFilterConfig.minFilter.isMipMapped) 1.3f else 1.0f
        return SizeInBytes( ((mipMapFactor * pixelCount * internalFormat.bitsPerPixel) / 8f).toInt()) // TODO: Account for multiple of 8
    }
    val gpuSizeOfImageInBytes: SizeInBytes get() {
        return SizeInBytes( ((pixelCount * internalFormat.bitsPerPixel) / 8f).toInt()) // TODO: Account for multiple of 8
    }

    data class Texture2DDescription(
        override val dimension: TextureDimension2D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
        override val wrapMode: WrapMode,
    ) : TextureDescription

    data class CubeMapDescription(
        override val dimension: TextureDimension2D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
        override val wrapMode: WrapMode,
    ) : TextureDescription

    data class CubeMapArrayDescription(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
        override val wrapMode: WrapMode,
    ) : TextureDescription

    data class Texture3DDescription(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
        override val wrapMode: WrapMode,
    ) : TextureDescription
}

data class ImageData(val width: Int, val height: Int, val mipMapLevel: Int, val dataProvider: () -> ByteBuffer)


fun createSingleMipLevelTexture2DUploadInfo(
    image: BufferedImage
): ImageData {
    return ImageData(image.width, image.height, 0) { image.resize(image.width).toByteBuffer() }
}
fun createAllMipLevelsImageData(
    image: BufferedImage
): List<ImageData> {
    val mipMapSizes = calculateMipMapSizes(image.width, image.height)

    return mipMapSizes.mapIndexed { index, it ->
        ImageData(it.width, it.height, mipMapSizes.size - 1 - index) { image.resize(it.width).toByteBuffer() }
    }
}
fun createAllMipLevelsTexture2DUploadInfo(
    files: List<File>,
    width: Int, height: Int,
    internalFormat: InternalTextureFormat
): Pair<Texture2DDescription, List<ImageData>> {
    val mipMapSizes = calculateMipMapSizes(width, height)

    val data = mipMapSizes.mapIndexed { index, it ->
        ImageData(it.width, it.height, mipMapSizes.size - 1 - index) {
            logger.debug("Loading texture ${files[index]}")
            ImageIO.read(files[index]).toByteBuffer()
        }
    }
    return Pair(Texture2DDescription(
        TextureDimension(width, height), internalFormat = internalFormat,
        textureFilterConfig = TextureFilterConfig(), wrapMode = WrapMode.Repeat,
    ), data)
}