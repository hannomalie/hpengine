package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.constants.TextureTarget

data class OpenGLTexture2DArray(
    override val description: Texture2DArrayDescription,
    override val id: Int,
    override val target: TextureTarget,
    override var handle: Long,
) : Texture2DArray
