package engine.graphics.query;

import org.lwjgl.opengl.GL33;

public enum Target {
    TIME_ELAPSED(GL33.GL_TIME_ELAPSED);

    public final int glTarget;

    Target(int glTarget) {
        this.glTarget = glTarget;
    }
}
