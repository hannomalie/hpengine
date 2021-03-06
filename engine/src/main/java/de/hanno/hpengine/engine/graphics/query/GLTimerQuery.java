package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.graphics.GpuContext;
import kotlin.Unit;
import org.lwjgl.opengl.GL15;

import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.*;

public class GLTimerQuery implements GLQuery<Float> {

    private GpuContext<OpenGl> gpuContext;

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
        return gpuContext.invoke(GL15::glGenQueries);
    }

    @Override
    public GLTimerQuery begin() {
        finished = false;
        gpuContext.invoke(() -> {
            glQueryCounter(start, GL_TIMESTAMP);
            return Unit.INSTANCE;
        });
        started = true;
        return this;
    }

    @Override
    public void end() {
        if(!started) {
            throw new IllegalStateException("Don't end a query before it was started!");
        }
        gpuContext.invoke(() -> {
            glQueryCounter(end, GL_TIMESTAMP);
            return Unit.INSTANCE;
        });
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
        return gpuContext.invoke(() -> glGetQueryObjectui64(start, GL_QUERY_RESULT));
    }

    public long getEndTime() {
        return gpuContext.invoke(() -> glGetQueryObjectui64(end, GL_QUERY_RESULT));
    }

    @Override
    public Float getResult() {
        if(!finished) { throw new IllegalStateException("Don't query result before query is finished!"); }
        while(!resultsAvailable(gpuContext)) {
        }
        return getTimeTaken() / 1000000f;
    }
}
