package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureTarget

data class OpenGLCubeMap(
    override val description: TextureDescription.CubeMapDescription,
    override val id: Int,
    override val target: TextureTarget,
    override var handle: Long,
) : CubeMap {
    companion object {

        operator fun invoke(
            graphicsApi: GraphicsApi,
            description: TextureDescription.CubeMapDescription,
            ): OpenGLCubeMap {
            val (textureId, handle) = graphicsApi.allocateTexture(
                description,
                TextureTarget.TEXTURE_CUBE_MAP,
            )
            return OpenGLCubeMap(
                description,
                textureId,
                TextureTarget.TEXTURE_CUBE_MAP,
                handle,
            )
        }
    }
}