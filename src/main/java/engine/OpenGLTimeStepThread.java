package engine;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.SharedDrawable;

public abstract class OpenGLTimeStepThread extends TimeStepThread {

    private SharedDrawable drawable;
    private long start = 0l;
    private long lastFrame = 0l;
    public volatile boolean initialized = false;
    private String name;

    public OpenGLTimeStepThread(String name) {
        super(name, 0.0f);
        this.name = name;

        try {
            drawable = new SharedDrawable(Display.getDrawable());
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        setDaemon(true);
        super.start();
        setName(name);
        start = System.currentTimeMillis();
        lastFrame = System.currentTimeMillis();
    }
    public void run() {
        initialized = true;
        Thread.currentThread().setName(name);

        while(!stopRequested) {
            long ms = System.currentTimeMillis() - lastFrame;
            try {
                drawable.makeCurrent();
                update(ms / 1000f);
            }catch (Exception e) {
                e.printStackTrace();
            }
            lastFrame = System.currentTimeMillis();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public abstract void update(float seconds);
}
