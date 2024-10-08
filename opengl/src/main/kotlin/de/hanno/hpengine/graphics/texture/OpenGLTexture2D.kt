package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.constants.TextureTarget

data class OpenGLTexture2D(
    override val description: TextureDescription.Texture2DDescription,
    override val id: Int,
    override val target: TextureTarget,
    override var handle: Long,
) : Texture2D

data class OpenGLTexture2DView(
    val index: Int,
    val underlying: OpenGLTexture2D,
) : Texture2D by underlying
