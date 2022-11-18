package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import org.lwjgl.opengl.GL11

data class ColorAttachmentDefinition(
    val name: String,
    val internalFormat: Int = GL11.GL_RGB8,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
) {
    fun setInternalFormat(internalFormat: Int): ColorAttachmentDefinition {
        return copy(internalFormat = internalFormat) // TODO: Use this pattern everywhere
    }

    fun setTextureFilter(textureFilter: TextureFilterConfig): ColorAttachmentDefinition {
        this.textureFilter = textureFilter
        return this
    }
}

data class ColorAttachmentDefinitions(
    val names: Array<String>,
    var internalFormat: Int = GL11.GL_RGB,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
)

fun ColorAttachmentDefinitions.toList(): List<ColorAttachmentDefinition> {
    return names.map { ColorAttachmentDefinition(it, internalFormat, textureFilter) }
}
