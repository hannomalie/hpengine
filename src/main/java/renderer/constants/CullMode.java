package renderer.constants;

import org.lwjgl.opengl.GL11;

public enum CullMode {
    FRONT(GL11.GL_FRONT), BACK(GL11.GL_BACK);

    public final int glMode;

    CullMode(int glMode) {
        this.glMode = glMode;
    }
}
