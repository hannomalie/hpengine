package de.hanno.hpengine.graphics.constants

enum class TextureTarget {
    TEXTURE_2D,
    TEXTURE_CUBE_MAP,
    TEXTURE_CUBE_MAP_ARRAY,
    TEXTURE_2D_ARRAY,
    TEXTURE_3D
}

val TextureTarget.is3D: Boolean get() = when(this) {
    TextureTarget.TEXTURE_2D -> false
    TextureTarget.TEXTURE_CUBE_MAP -> false
    TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> true
    TextureTarget.TEXTURE_2D_ARRAY -> true
    TextureTarget.TEXTURE_3D -> true
}