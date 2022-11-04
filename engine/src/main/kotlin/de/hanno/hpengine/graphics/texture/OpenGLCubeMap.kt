package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import org.lwjgl.opengl.GL11

data class OpenGLCubeMap(
    override val dimension: TextureDimension2D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: Int,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: Int,
    override var uploadState: UploadState
) : CubeMap {
    companion object {
        operator fun invoke(
            gpuContext: GpuContext,
            dimension: TextureDimension2D,
            filterConfig: TextureFilterConfig,
            internalFormat: Int,
            wrapMode: Int = GL11.GL_REPEAT
        ): OpenGLCubeMap {
            val (textureId, internalFormat, handle) = gpuContext.allocateTexture(
                UploadInfo.Texture2DUploadInfo(dimension),
                TextureTarget.TEXTURE_CUBE_MAP,
                filterConfig,
                internalFormat,
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
                UploadState.UPLOADED
            )
        }
    }
}