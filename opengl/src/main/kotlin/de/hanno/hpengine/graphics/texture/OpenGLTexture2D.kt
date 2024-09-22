package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode

data class OpenGLTexture2D(
    override val dimension: TextureDimension2D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
    override val wrapMode: WrapMode,
    var initialUploadState: UploadState,
    override var currentMipMapBias: Float, // TODO: Adjust the bias correctly when setting state
    override val srgba: Boolean = false,
    override var unloadable: Boolean = true,
) : Texture2D {
    override var uploadState: UploadState = initialUploadState
        set(value) {
            when(value) {
                is UploadState.MarkedForUpload -> {
                    currentMipMapBias = value.mipMapLevel.toFloat()
                }
                UploadState.Uploaded -> {}
                is UploadState.Uploading -> {
                    if(currentMipMapBias < value.mipMapLevel + 1) {
                        currentMipMapBias = value.mipMapLevel + 1f
                    }
                }
                is UploadState.Unloaded -> {
                    currentMipMapBias = value.mipMapLevel.toFloat()
                }
            }
            field = value
        }
}

data class OpenGLTexture2DView(
    val index: Int,
    val underlying: OpenGLTexture2D,
) : Texture2D by underlying
