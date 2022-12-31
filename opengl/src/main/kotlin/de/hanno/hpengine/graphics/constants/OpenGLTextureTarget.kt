package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.*


val TextureTarget.glValue: Int get() = when(this) {
    TextureTarget.TEXTURE_2D -> GL11.GL_TEXTURE_2D
    TextureTarget.TEXTURE_CUBE_MAP -> GL13.GL_TEXTURE_CUBE_MAP
    TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> GL40.GL_TEXTURE_CUBE_MAP_ARRAY
    TextureTarget.TEXTURE_2D_ARRAY -> GL30.GL_TEXTURE_2D_ARRAY
    TextureTarget.TEXTURE_3D -> GL12.GL_TEXTURE_3D
}