package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.logger
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt


sealed interface UploadState {
    data class Unloaded(val mipMapLevel: Int) : UploadState
    data class Uploading(val mipMapLevel: Int): UploadState
    data class MarkedForUpload(val mipMapLevel: Int): UploadState
    data object Uploaded : UploadState
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

sealed interface UploadInfo {
    val dimension: TextureDimension
    val internalFormat: InternalTextureFormat
    val textureFilterConfig: TextureFilterConfig
    val mipMapCount: Int

    sealed interface Texture2DUploadInfo: UploadInfo {
        override val dimension: TextureDimension2D
        val dataCompressed: Boolean
        val srgba: Boolean
    }

    data class SingleMipLevelTexture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: LazyTextureData?,
        override val dataCompressed: Boolean = false,
        override val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig
    ) : Texture2DUploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1
    }

    data class AllMipLevelsTexture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: List<LazyTextureData>,
        override val dataCompressed: Boolean = false,
        override val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig
    ) : Texture2DUploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1

        init {
            require(mipMapCount == data.size) {
                "Provided mipmap count plus main image (${data.size}) is unequal to expected mipmap count ($mipMapCount)!"
            }
        }
    }


    data class Texture3DUploadInfo(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1
    }

    data class CubeMapUploadInfo(
        override val dimension: TextureDimension2D,
        val buffers: List<ByteBuffer> = emptyList(),
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1
    }
    data class CubeMapArrayUploadInfo(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1
    }
}

data class LazyTextureData(val width: Int, val height: Int, val dataProvider: () -> ByteBuffer)


fun createAllMipLevelsLazyTexture2DUploadInfo(
    image: BufferedImage,
    internalFormat: InternalTextureFormat
): UploadInfo.AllMipLevelsTexture2DUploadInfo {
    val srgba =
        internalFormat == InternalTextureFormat.SRGB8_ALPHA8 || internalFormat == InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5
    val mipMapSizes = calculateMipMapSizes(image.width, image.height)

    val data = mipMapSizes.map {
        LazyTextureData(it.width, it.height) { image.resize(it.width).toByteBuffer() }
    }
    return UploadInfo.AllMipLevelsTexture2DUploadInfo(
        TextureDimension(image.width, image.height), data, false, srgba,
        internalFormat = internalFormat,
        textureFilterConfig = TextureFilterConfig(),
    )
}
fun createAllMipLevelsLazyTexture2DUploadInfo(
    files: List<File>,
    width: Int, height: Int,
    internalFormat: InternalTextureFormat
): UploadInfo.AllMipLevelsTexture2DUploadInfo {
    val srgba =
        internalFormat == InternalTextureFormat.SRGB8_ALPHA8 || internalFormat == InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5
    val mipMapSizes = calculateMipMapSizes(width, height)

    val data = mipMapSizes.mapIndexed { index, it ->
        LazyTextureData(it.width, it.height) {
            logger.debug("Loading texture ${files[index]}")
            ImageIO.read(files[index]).toByteBuffer()
        }
    }
    return UploadInfo.AllMipLevelsTexture2DUploadInfo(
        TextureDimension(width, height), data, false, srgba,
        internalFormat = internalFormat,
        textureFilterConfig = TextureFilterConfig(),
    )
}