package de.hanno.hpengine.renderer.constants;

import org.lwjgl.opengl.GL11;

public enum GlDepthFunc {
    LESS(GL11.GL_LESS), LEQUAL(GL11.GL_LEQUAL);

    public final int glFunc;

    GlDepthFunc(int glFunc) {
        this.glFunc = glFunc;
    }
}