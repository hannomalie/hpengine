package de.hanno.hpengine.graphics.renderer.constants

val TextureTarget.is3D: Boolean get() = when(this) {
    TextureTarget.TEXTURE_2D -> false
    TextureTarget.TEXTURE_CUBE_MAP -> false
    TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> true
    TextureTarget.TEXTURE_2D_ARRAY -> true
    TextureTarget.TEXTURE_3D -> true
}