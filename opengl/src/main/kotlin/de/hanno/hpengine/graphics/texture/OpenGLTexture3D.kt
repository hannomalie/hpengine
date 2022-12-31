package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode

data class OpenGLTexture3D(
    override val dimension: TextureDimension3D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: WrapMode,
    override var uploadState: UploadState
) : Texture3D {
    companion object {
        context(GraphicsApi)
        operator fun invoke(
            dimension: TextureDimension3D,
            filterConfig: TextureFilterConfig,
            internalFormat: InternalTextureFormat,
            wrapMode: WrapMode = WrapMode.Repeat
        ): OpenGLTexture3D {
            val (textureId, handle) = allocateTexture(
                UploadInfo.Texture3DUploadInfo(
                    dimension,
                    internalFormat = internalFormat,
                    textureFilterConfig = filterConfig
                ),
                TextureTarget.TEXTURE_3D,
                wrapMode
            )
            return OpenGLTexture3D(
                dimension,
                textureId,
                TextureTarget.TEXTURE_3D,
                internalFormat,
                handle,
                filterConfig,
                wrapMode,
                UploadState.UPLOADED
            )
        }
    }
}