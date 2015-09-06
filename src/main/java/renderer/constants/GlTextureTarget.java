package renderer.constants;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL40;

public enum GlTextureTarget {

    TEXTURE_2D(GL11.GL_TEXTURE_2D),
    TEXTURE_CUBE_MAP(GL13.GL_TEXTURE_CUBE_MAP),
    TEXTURE_CUBE_MAP_ARRAY(GL40.GL_TEXTURE_CUBE_MAP_ARRAY);

    public final int glTarget;

    GlTextureTarget(int target) {
        this.glTarget = target;
    }
}
