package de.hanno.hpengine.graphics.texture

data class TextureAllocationData(
    val textureId: Int,
    val internalFormat: Int,
    val handle: Long,
    val wrapMode: Int
)