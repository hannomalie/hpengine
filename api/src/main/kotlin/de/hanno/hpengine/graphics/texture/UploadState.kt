package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt


sealed interface UploadState {
    object NotUploaded : UploadState {
        override fun toString() = "NotUploaded"
    }
    data class Uploading(val maxMipMapLoaded: Int): UploadState
    object Uploaded : UploadState {
        override fun toString() = "Uploaded"
    }
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
        targetWidth,
        targetHeight,
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

        companion object {
            operator fun invoke(
                dimension: TextureDimension2D,
                data: ByteBuffer? = null,
                dataCompressed: Boolean = false,
                srgba: Boolean = false,
                internalFormat: InternalTextureFormat,
                textureFilterConfig: TextureFilterConfig,
            ): SimpleTexture2DUploadInfo {
                return SimpleTexture2DUploadInfo(
                    dimension,
                    data,
                    dataCompressed,
                    srgba,
                    internalFormat,
                    textureFilterConfig,
                )
            }
        }
    }

    data class SimpleTexture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: ByteBuffer? = null,
        override val dataCompressed: Boolean = false,
        override val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : Texture2DUploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1
    }

    data class CompleteTexture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: List<ByteBuffer>,
        override val dataCompressed: Boolean = false,
        override val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : Texture2DUploadInfo {
        override val mipMapCount: Int = if(textureFilterConfig.minFilter.isMipMapped) data.size else 1
    }

    data class LazyTexture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: List<Foo>,
        override val dataCompressed: Boolean = false,
        override val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
        override val mipMapCount: Int
    ) : Texture2DUploadInfo

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

// TODO: Rename properly
data class Foo(val width: Int, val height: Int, val dataProvider: () -> ByteBuffer)
