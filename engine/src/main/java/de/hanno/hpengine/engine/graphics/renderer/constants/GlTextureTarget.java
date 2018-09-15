package de.hanno.hpengine.engine.graphics.renderer.constants;

import org.lwjgl.opengl.*;

public enum GlTextureTarget {

    TEXTURE_2D(GL11.GL_TEXTURE_2D, false),
    TEXTURE_CUBE_MAP(GL13.GL_TEXTURE_CUBE_MAP, false),
    TEXTURE_CUBE_MAP_ARRAY(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, true),
    TEXTURE_2D_ARRAY(GL30.GL_TEXTURE_2D_ARRAY, true),
    TEXTURE_3D(GL12.GL_TEXTURE_3D, true);

    public final int glTarget;
    public final boolean is3D;

    GlTextureTarget(int target, boolean is3D) {
        this.glTarget = target;
        this.is3D = is3D;
    }

}
