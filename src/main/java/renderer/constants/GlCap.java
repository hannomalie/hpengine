package renderer.constants;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

public enum GlCap {
    TEXTURE_CUBE_MAP_SEAMLESS(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS),
    DEPTH_TEST(GL11.GL_DEPTH_TEST),
    CULL_FACE(GL11.GL_CULL_FACE),
    BLEND(GL11.GL_BLEND);

    public final int glInt;
    public boolean enabled = false;

    GlCap(int glInt) {
        this.glInt = glInt;
        enabled = GL11.glIsEnabled(glInt);
    }

    public void enable() {
        if (!enabled) {
            GL11.glEnable(glInt);
            enabled = true;
        }
    }

    public void disable() {
        if (enabled) {
            GL11.glDisable(glInt);
            enabled = false;
        }
    }
}
