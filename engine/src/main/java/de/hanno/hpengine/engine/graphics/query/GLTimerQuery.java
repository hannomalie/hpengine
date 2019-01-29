package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.graphics.GpuContext;

import java.util.concurrent.Callable;

import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.*;

public class GLTimerQuery implements GLQuery<Float> {

    private GpuContext gpuContext;

    private final int start;
    private final int end;
    private volatile boolean finished = false;
    private boolean started;

    public GLTimerQuery(GpuContext gpuContext) {
        this.gpuContext = gpuContext;
        start = glGenQuery();
        end = glGenQuery();
    }

    private int glGenQuery() {
        return gpuContext.calculate((Callable<Integer>) () -> glGenQueries());
    }

    @Override
    public GLTimerQuery begin() {
        finished = false;
        gpuContext.execute(() -> {
            glQueryCounter(start, GL_TIMESTAMP);
        }, true);
        started = true;
        return this;
    }

    @Override
    public void end() {
        if(!started) {
            throw new IllegalStateException("Don't end a query before it was started!");
        }
        gpuContext.execute(() -> {
            glQueryCounter(end, GL_TIMESTAMP);
        }, true);
        finished = true;
    }


    public long getTimeTaken() {
        return getEndTime() - getStartTime();
    }

    @Override
    public int getQueryToWaitFor() {
        return start;
    }

    public long getStartTime() {
        return gpuContext.calculate((Callable<Long>) () -> glGetQueryObjectui64(start, GL_QUERY_RESULT));
    }

    public long getEndTime() {
        return gpuContext.calculate((Callable<Long>) () -> glGetQueryObjectui64(end, GL_QUERY_RESULT));
    }

    @Override
    public Float getResult() {
        if(!finished) { throw new IllegalStateException("Don't query result before query is finished!"); }
        while(!resultsAvailable(gpuContext)) {
        }
        return getTimeTaken() / 1000000f;
    }
}
