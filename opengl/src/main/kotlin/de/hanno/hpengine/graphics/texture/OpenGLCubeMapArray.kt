package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.constants.TextureTarget

data class OpenGLCubeMapArray(
    override val description: TextureDescription.CubeMapArrayDescription,
    override val id: Int,
    override val target: TextureTarget = TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
    override var handle: Long,
) : CubeMapArray {
    val size: Int get() = dimension.depth
}