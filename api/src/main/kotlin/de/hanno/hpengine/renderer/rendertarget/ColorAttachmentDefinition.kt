package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig

data class ColorAttachmentDefinition(
    val name: String,
    val internalFormat: Int,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
)

data class ColorAttachmentDefinitions(
    val names: Array<String>,
    var internalFormat: Int,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
)

fun ColorAttachmentDefinitions.toList() = names.map { ColorAttachmentDefinition(it, internalFormat, textureFilter) }
