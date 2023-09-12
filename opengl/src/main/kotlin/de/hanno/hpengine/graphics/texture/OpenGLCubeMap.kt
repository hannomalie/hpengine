package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode

data class OpenGLCubeMap(
    override val dimension: TextureDimension2D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: WrapMode,
    override var uploadState: UploadState
) : CubeMap {
    companion object {

        operator fun invoke(
            graphicsApi: GraphicsApi,
            dimension: TextureDimension2D,
            filterConfig: TextureFilterConfig,
            internalFormat: InternalTextureFormat,
            wrapMode: WrapMode = WrapMode.Repeat,
        ): OpenGLCubeMap {
            val (textureId, handle) = graphicsApi.allocateTexture(
                UploadInfo.Texture2DUploadInfo(dimension, internalFormat = internalFormat, textureFilterConfig = filterConfig),
                TextureTarget.TEXTURE_CUBE_MAP,
                wrapMode
            )
            return OpenGLCubeMap(
                dimension,
                textureId,
                TextureTarget.TEXTURE_CUBE_MAP,
                internalFormat,
                handle,
                filterConfig,
                wrapMode,
                UploadState.Uploaded
            )
        }
    }
}