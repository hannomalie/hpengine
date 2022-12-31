package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import java.nio.ByteBuffer

enum class UploadState {
    NOT_UPLOADED,
    UPLOADING,
    UPLOADED
}

sealed interface UploadInfo {
    val dimension: TextureDimension
    val internalFormat: InternalTextureFormat
    val textureFilterConfig: TextureFilterConfig

    data class Texture2DUploadInfo(
        override val dimension: TextureDimension2D,
        val data: ByteBuffer? = null,
        val dataCompressed: Boolean = false,
        val srgba: Boolean = false,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
        }
        val mipMapCount = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1 // TODO: Think about whether 1 is correct here
    }

    data class Texture3DUploadInfo(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
            require(dimension.depth > 0) { "Illegal depth $dimension" }
        }

        val mipMapCount = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1 // TODO: Think about whether 1 is correct here
    }

    data class CubeMapUploadInfo(
        override val dimension: TextureDimension2D,
        val buffers: List<ByteBuffer> = emptyList(),
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
        }
        val mipMapCount = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1 // TODO: Think about whether 1 is correct here

    }
    data class CubeMapArrayUploadInfo(
        override val dimension: TextureDimension3D,
        override val internalFormat: InternalTextureFormat,
        override val textureFilterConfig: TextureFilterConfig,
    ) : UploadInfo {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
            require(dimension.depth > 0) { "Illegal depth $dimension" }
        }
        val mipMapCount = if(textureFilterConfig.minFilter.isMipMapped) dimension.getMipMapCount() else 1 // TODO: Think about whether 1 is correct here

    }
}
