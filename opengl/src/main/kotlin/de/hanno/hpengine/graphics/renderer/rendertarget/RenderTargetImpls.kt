package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.*
import de.hanno.hpengine.graphics.texture.*


context(GpuContext)
class DepthBuffer<T : Texture>(val texture: T) {

    companion object {
        context(GpuContext)
        operator fun invoke(width: Int, height: Int): DepthBuffer<Texture2D> {
            val dimension = TextureDimension(width, height)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            val textureTarget = TextureTarget.TEXTURE_2D
            val internalFormat = InternalTextureFormat.DEPTH_COMPONENT
            val (textureId, handle, wrapMode) = allocateTexture(
                UploadInfo.Texture2DUploadInfo(dimension, internalFormat = internalFormat),
                textureTarget,
                filterConfig,
                WrapMode.Repeat,
            )

            return DepthBuffer(
                Texture2D(
                    dimension,
                    textureTarget,
                    internalFormat,
                    filterConfig,
                    wrapMode,
                    UploadState.UPLOADED
                )
            )
        }
    }
}