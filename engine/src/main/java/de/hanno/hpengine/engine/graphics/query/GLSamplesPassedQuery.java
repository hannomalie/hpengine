package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import org.lwjgl.opengl.GL15;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

public class GLSamplesPassedQuery implements GLQuery<Integer> {

    private final int query;
    private final int target = GL15.GL_SAMPLES_PASSED;
    private volatile boolean finished = false;
    private boolean started;

    public GLSamplesPassedQuery() {

        query = GraphicsContext.getInstance().calculate(() -> glGenQueries());
    }

    @Override
    public void begin() {
        GraphicsContext.getInstance().execute(() -> {
            glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
        });
        started = true;
    }

    @Override
    public void end() {
        if(!started) {
            throw new IllegalStateException("Don't end a query before it was started!");
        }
        GraphicsContext.getInstance().execute(() -> {
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
        while(!resultsAvailable()) {
        }

        return GraphicsContext.getInstance().calculate( () -> (int) glGetQueryObjectui64(getQueryToWaitFor(), GL_QUERY_RESULT));
    }
}
