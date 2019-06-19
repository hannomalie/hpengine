package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.opengl.GL15;

import java.util.concurrent.Callable;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

public class GLSamplesPassedQuery implements GLQuery<Integer> {

    private final int query;
    private final int target = GL15.GL_SAMPLES_PASSED;
    private final GpuContext<OpenGl> gpuContext;
    private volatile boolean finished = false;
    private boolean started;

    public GLSamplesPassedQuery(GpuContext gpuContext) {
        this.gpuContext = gpuContext;
        query = this.gpuContext.calculate((Callable<Integer>) () -> glGenQueries());
    }

    @Override
    public GLTimerQuery begin() {
        gpuContext.execute("GLSamplesPassedQuery.begin", () -> {
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
        gpuContext.execute("GLSamplesPassedQuery.end", () -> {
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

        return gpuContext.calculate((Callable<Integer>) () -> (int) glGetQueryObjectui64(getQueryToWaitFor(), GL_QUERY_RESULT));
    }
}
