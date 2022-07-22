package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.*

enum class GlTextureTarget(val glTarget: Int, val is3D: Boolean) {
    TEXTURE_2D(GL11.GL_TEXTURE_2D, false), TEXTURE_CUBE_MAP(GL13.GL_TEXTURE_CUBE_MAP, false), TEXTURE_CUBE_MAP_ARRAY(
        GL40.GL_TEXTURE_CUBE_MAP_ARRAY, true
    ),
    TEXTURE_2D_ARRAY(GL30.GL_TEXTURE_2D_ARRAY, true), TEXTURE_3D(GL12.GL_TEXTURE_3D, true);
}