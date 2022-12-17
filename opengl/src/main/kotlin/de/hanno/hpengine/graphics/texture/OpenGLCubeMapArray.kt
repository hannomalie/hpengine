package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.WrapMode

data class OpenGLCubeMapArray(
    override val dimension: TextureDimension3D,
    override val id: Int,
    override val target: TextureTarget = TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
    override val internalFormat: InternalTextureFormat,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: WrapMode,
    override var uploadState: UploadState
) : CubeMapArray {

    val size: Int get() = dimension.depth

    companion object {

        context(GpuContext)
        operator fun invoke(
            dimension: TextureDimension3D,
            filterConfig: TextureFilterConfig,
            internalFormat: InternalTextureFormat,
            wrapMode: WrapMode
        ): OpenGLCubeMapArray {
            val (textureId, handle) = allocateTexture(
                UploadInfo.CubeMapArrayUploadInfo(dimension, internalFormat = internalFormat),
                TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
                filterConfig,
                wrapMode
            )
            return OpenGLCubeMapArray(
                dimension,
                textureId,
                TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
                internalFormat,
                handle,
                filterConfig,
                wrapMode,
                UploadState.UPLOADED
            )
        }
    }
}