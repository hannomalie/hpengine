package de.hanno.hpengine.graphics.texture

import org.lwjgl.opengl.GL13.*

val CubeMapSide.glValue: Int
    get() = when(this) {
        CubeMapSide.PositiveX -> GL_TEXTURE_CUBE_MAP_POSITIVE_X
        CubeMapSide.NegativeX -> GL_TEXTURE_CUBE_MAP_NEGATIVE_X
        CubeMapSide.PositiveY -> GL_TEXTURE_CUBE_MAP_POSITIVE_Y
        CubeMapSide.NegativeY -> GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
        CubeMapSide.PositiveZ -> GL_TEXTURE_CUBE_MAP_POSITIVE_Z
        CubeMapSide.NegativeZ -> GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
    }





