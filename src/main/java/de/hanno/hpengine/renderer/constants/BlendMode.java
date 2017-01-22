package de.hanno.hpengine.renderer.constants;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public enum BlendMode {
    FUNC_ADD(GL14.GL_FUNC_ADD);

    public final int mode;

    BlendMode(int mode) {
        this.mode = mode;
    }


    public enum Factor {
        ONE(GL11.GL_ONE),
        SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA);

        public final int glFactor;

        Factor(int glFactor) {
            this.glFactor = glFactor;
        }
    }
}
