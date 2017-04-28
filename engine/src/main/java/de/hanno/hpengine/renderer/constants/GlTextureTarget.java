package de.hanno.hpengine.renderer.constants;

import org.lwjgl.opengl.*;

public enum GlTextureTarget {

    TEXTURE_2D(GL11.GL_TEXTURE_2D),
    TEXTURE_CUBE_MAP(GL13.GL_TEXTURE_CUBE_MAP),
    TEXTURE_CUBE_MAP_ARRAY(GL40.GL_TEXTURE_CUBE_MAP_ARRAY),
    TEXTURE_2D_ARRAY(GL30.GL_TEXTURE_2D_ARRAY),
    TEXTURE_3D(GL12.GL_TEXTURE_3D);

    public final int glTarget;

    GlTextureTarget(int target) {
        this.glTarget = target;
    }
}
