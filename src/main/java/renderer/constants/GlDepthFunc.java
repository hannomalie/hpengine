package renderer.constants;

import org.lwjgl.opengl.GL11;

public enum GlDepthFunc {
    LESS(GL11.GL_LESS);

    public final int glFunc;

    GlDepthFunc(int glFunc) {
        this.glFunc = glFunc;
    }
}
