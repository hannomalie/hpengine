package de.hanno.hpengine.graphics.constants

import CubeMapFace
import CubeMapFace.*
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X

val CubeMapFace.glValue
    get() = when(this) {
        NEGATIVE_X -> GL_TEXTURE_CUBE_MAP_POSITIVE_X + 1
        POSITIVE_Y -> GL_TEXTURE_CUBE_MAP_POSITIVE_X + 1
        NEGATIVE_Y -> GL_TEXTURE_CUBE_MAP_POSITIVE_X + 1
        POSITIVE_Z -> GL_TEXTURE_CUBE_MAP_POSITIVE_X + 1
        NEGATIVE_Z -> GL_TEXTURE_CUBE_MAP_POSITIVE_X + 1
        POSITIVE_X -> GL_TEXTURE_CUBE_MAP_POSITIVE_X
    }