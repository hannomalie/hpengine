package de.hanno.hpengine.engine.graphics.query;

import de.hanno.hpengine.renderer.GraphicsContext;

import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.*;

public class GLTimerQuery implements GLQuery<Float> {

    private static GLTimerQuery instance;
    public static GLTimerQuery getInstance() {
        if(instance == null) {
            instance = new GLTimerQuery();
        }
        return instance;
    }

    private final int start;
    private final int end;
    private volatile boolean finished = false;
    private boolean started;

    public GLTimerQuery() {
        start = glGenQuery();
        end = glGenQuery();
    }

    private int glGenQuery() {
        return GraphicsContext.getInstance().calculate( () -> glGenQueries());
    }

    @Override
    public void begin() {
        finished = false;
        GraphicsContext.getInstance().execute(() -> {
            glQueryCounter(start, GL_TIMESTAMP);
        }, true);
        started = true;
    }

    @Override
    public void end() {
        if(!started) {
            throw new IllegalStateException("Don't end a query before it was started!");
        }
        GraphicsContext.getInstance().execute(() -> {
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
        return GraphicsContext.getInstance().calculate( () -> glGetQueryObjectui64(start, GL_QUERY_RESULT));
    }

    public long getEndTime() {
        return GraphicsContext.getInstance().calculate( () -> glGetQueryObjectui64(end, GL_QUERY_RESULT));
    }

    @Override
    public Float getResult() {
        if(!finished) { throw new IllegalStateException("Don't query result before query is finished!"); }
        while(!resultsAvailable()) {
        }
        return getTimeTaken() / 1000000f;
    }
}
