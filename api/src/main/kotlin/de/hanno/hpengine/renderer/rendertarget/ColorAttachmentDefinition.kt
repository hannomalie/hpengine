package de.hanno.hpengine.graphics.renderer.rendertarget

import InternalTextureFormat
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig

data class ColorAttachmentDefinition(
    val name: String,
    val internalFormat: InternalTextureFormat,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
)

data class ColorAttachmentDefinitions(
    val names: Array<String>,
    var internalFormat: InternalTextureFormat,
    var textureFilter: TextureFilterConfig = TextureFilterConfig()
)

fun ColorAttachmentDefinitions.toList() = names.map { ColorAttachmentDefinition(it, internalFormat, textureFilter) }
