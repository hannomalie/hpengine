package de.hanno.hpengine.engine;

import org.lwjgl.opengl.*;
import de.hanno.hpengine.renderer.OpenGLContext;

public abstract class OpenGLTimeStepThread extends TimeStepThread {

    private SharedDrawable drawable;
    private long start = 0l;
    private long lastFrame = 0l;
    public volatile boolean initialized = false;
    private String name;

    public OpenGLTimeStepThread(String name) {
        this(name, OpenGLContext.getInstance().calculate(
                () -> new SharedDrawable(Display.getDrawable())
        ));
    }

    public OpenGLTimeStepThread(String name, SharedDrawable drawable) {
        super(name, 0.0f);
        this.name = name;
        this.drawable = drawable;
    }

    @Override
    public void start() {
        setDaemon(true);
        super.start();
        setName(name);
        start = System.currentTimeMillis();
        lastFrame = System.currentTimeMillis();
    }

    @Override
    public void run() {
        initialized = true;
        Thread.currentThread().setName(name);

        while(!stopRequested) {
            long ms = System.currentTimeMillis() - lastFrame;
            try {
                drawable.makeCurrent();
                update(ms / 1000f);
                drawable.releaseContext();
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
