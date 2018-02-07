package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.graphics.renderer.GpuContext;

import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

public interface GLQuery<RESULT> {
    GLTimerQuery begin();

    void end();

    default boolean resultsAvailable(GpuContext gpuContext) {
        return gpuContext.calculate( () -> glGetQueryObjectui64(getQueryToWaitFor(), GL_QUERY_RESULT_AVAILABLE)) == GL_TRUE;
    }

    int getQueryToWaitFor();

    RESULT getResult();
}
