package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget

data class OpenGLCubeMapArray(
    override val dimension: TextureDimension3D,
    override val id: Int,
    override val target: TextureTarget = TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
    override val internalFormat: Int,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: Int,
    override var uploadState: UploadState
) : CubeMapArray {

    val size: Int get() = dimension.depth

    companion object {
        operator fun invoke(
            gpuContext: GpuContext,
            dimension: TextureDimension3D,
            filterConfig: TextureFilterConfig,
            internalFormat: Int,
            wrapMode: Int
        ): OpenGLCubeMapArray {
            val (textureId, internalFormat, handle) = gpuContext.allocateTexture(
                UploadInfo.CubeMapArrayUploadInfo(dimension),
                TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
                filterConfig,
                internalFormat,
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