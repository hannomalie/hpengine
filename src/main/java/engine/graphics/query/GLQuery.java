package engine.graphics.query;

import renderer.OpenGLContext;

import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

public interface GLQuery<RESULT> {
    void begin();

    void end();

    default boolean resultsAvailable() {
        return OpenGLContext.getInstance().calculate( () -> glGetQueryObjectui64(getQueryToWaitFor(), GL_QUERY_RESULT_AVAILABLE)) == GL_TRUE;
    }

    int getQueryToWaitFor();

    RESULT getResult();
}
