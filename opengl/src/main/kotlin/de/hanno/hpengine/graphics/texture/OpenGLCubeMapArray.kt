package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode

data class OpenGLCubeMapArray(
    override val dimension: TextureDimension3D,
    override val id: Int,
    override val target: TextureTarget = TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: WrapMode,
    override var uploadState: UploadState,
    override var currentMipMapBias: Float = when(uploadState) {
        is UploadState.Unloaded -> dimension.getMipMapCount().toFloat()
        UploadState.Uploaded -> 0f
        is UploadState.Uploading -> uploadState.mipMapLevel.toFloat()
        is UploadState.MarkedForUpload -> uploadState.mipMapLevel.toFloat()
    },
    override var unloadable: Boolean = true
) : CubeMapArray {

    val size: Int get() = dimension.depth
}