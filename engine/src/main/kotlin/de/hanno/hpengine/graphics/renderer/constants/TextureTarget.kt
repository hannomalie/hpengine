package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.*

val TextureTarget.glTarget: Int get() = when(this) {
    TextureTarget.TEXTURE_2D -> GL11.GL_TEXTURE_2D
    TextureTarget.TEXTURE_CUBE_MAP -> GL13.GL_TEXTURE_CUBE_MAP
    TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> GL40.GL_TEXTURE_CUBE_MAP_ARRAY
    TextureTarget.TEXTURE_2D_ARRAY -> GL30.GL_TEXTURE_2D_ARRAY
    TextureTarget.TEXTURE_3D -> GL12.GL_TEXTURE_3D
}

val TextureTarget.is3D: Boolean get() = when(this) {
    TextureTarget.TEXTURE_2D -> false
    TextureTarget.TEXTURE_CUBE_MAP -> false
    TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> true
    TextureTarget.TEXTURE_2D_ARRAY -> true
    TextureTarget.TEXTURE_3D -> true
}