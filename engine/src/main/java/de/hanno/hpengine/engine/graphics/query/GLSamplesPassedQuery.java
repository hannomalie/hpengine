package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.opengl.GL15;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

public class GLSamplesPassedQuery implements GLQuery<Integer> {

    private final int query;
    private final int target = GL15.GL_SAMPLES_PASSED;
    private final GpuContext gpuContext;
    private volatile boolean finished = false;
    private boolean started;

    public GLSamplesPassedQuery(GpuContext gpuContext) {
        this.gpuContext = gpuContext;
        query = this.gpuContext.calculate(() -> glGenQueries());
    }

    @Override
    public GLTimerQuery begin() {
        gpuContext.execute(() -> {
            glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
        });
        started = true;
        return null;
    }

    @Override
    public void end() {
        if(!started) {
            throw new IllegalStateException("Don't end a query before it was started!");
        }
        gpuContext.execute(() -> {
            glEndQuery(target);
        });
        finished = true;
    }

    @Override
    public int getQueryToWaitFor() {
        return query;
    }

    @Override
    public Integer getResult() {
        if(!finished) { throw new IllegalStateException("Don't query result before query is finished!"); }
        while(!resultsAvailable(gpuContext)) {
        }

        return gpuContext.calculate( () -> (int) glGetQueryObjectui64(getQueryToWaitFor(), GL_QUERY_RESULT));
    }
}
