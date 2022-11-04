package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import org.lwjgl.opengl.GL11

data class OpenGLTexture3D(
    override val dimension: TextureDimension3D,
    override val id: Int,
    override val target: TextureTarget,
    override val internalFormat: Int,
    override var handle: Long,
    override val textureFilterConfig: TextureFilterConfig,
    override val wrapMode: Int,
    override var uploadState: UploadState
) : Texture3D {
    companion object {
        operator fun invoke(
            gpuContext: GpuContext,
            dimension: TextureDimension3D,
            filterConfig: TextureFilterConfig,
            internalFormat: Int,
            wrapMode: Int = GL11.GL_REPEAT
        ): OpenGLTexture3D {
            val (textureId, internalFormat, handle) = allocateTexture(
                gpuContext,
                OpenGLTexture2D.TextureUploadInfo.Texture3DUploadInfo(dimension),
                TextureTarget.TEXTURE_3D,
                filterConfig,
                internalFormat,
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