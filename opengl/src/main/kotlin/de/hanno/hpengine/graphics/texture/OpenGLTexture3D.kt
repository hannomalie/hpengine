package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureTarget

data class OpenGLTexture3D(
    override val description: TextureDescription.Texture3DDescription,
    override val id: Int,
    override val target: TextureTarget,
    override var handle: Long,
) : Texture3D {
    companion object {
        operator fun invoke(
            graphicsApi: GraphicsApi,
            description: TextureDescription.Texture3DDescription
        ): OpenGLTexture3D {
            val (textureId, handle) = graphicsApi.allocateTexture(
                description,
                TextureTarget.TEXTURE_3D,
            )
            return OpenGLTexture3D(
                description,
                textureId,
                TextureTarget.TEXTURE_3D,
                handle,
            )
        }
    }
}
