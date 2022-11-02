package de.hanno.hpengine.graphics.renderer.constants

data class TextureFilterConfig(
    val minFilter: MinFilter = MinFilter.LINEAR_MIPMAP_LINEAR,
    val magFilter: MagFilter = MagFilter.LINEAR,
)

enum class MinFilter(val isMipMapped: Boolean = false) {
    NEAREST(),
    LINEAR(),
    NEAREST_MIPMAP_NEAREST(true),
    LINEAR_MIPMAP_NEAREST(true),
    NEAREST_MIPMAP_LINEAR(true),
    LINEAR_MIPMAP_LINEAR(true)
}

enum class MagFilter {
    NEAREST,
    LINEAR
}


