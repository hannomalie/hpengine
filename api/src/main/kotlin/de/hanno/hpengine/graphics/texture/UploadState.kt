package de.hanno.hpengine.graphics.texture

import java.nio.ByteBuffer

enum class UploadState {
    NOT_UPLOADED,
    UPLOADING,
    UPLOADED
}

sealed class UploadInfo {
    data class Texture2DUploadInfo(
        val dimension: TextureDimension2D,
        val data: ByteBuffer? = null,
        val dataCompressed: Boolean = false,
        val srgba: Boolean = false
    ) : UploadInfo() {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
        }
    }

    data class Texture3DUploadInfo(val dimension: TextureDimension3D) : UploadInfo() {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
            require(dimension.depth > 0) { "Illegal depth $dimension" }
        }
    }

    data class CubeMapUploadInfo(
        val dimension: TextureDimension2D,
        val buffers: List<ByteBuffer> = emptyList()
    ) : UploadInfo() {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
        }
    }
    data class CubeMapArrayUploadInfo(val dimension: TextureDimension3D) : UploadInfo() {
        init {
            require(dimension.width > 0) { "Illegal width $dimension" }
            require(dimension.height > 0) { "Illegal height $dimension" }
            require(dimension.depth > 0) { "Illegal depth $dimension" }
        }
    }
}
