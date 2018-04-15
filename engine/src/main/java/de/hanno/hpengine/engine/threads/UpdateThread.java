package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.OpenGLContext;

public class UpdateThread extends FpsCountedTimeStepThread {
    private static long UPDATE_THREAD_ID = -1;

    private Engine engine;


    public UpdateThread(Engine engine, String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
        this.engine = engine;
    }

    @Override
    public void update(float seconds) {
        try {
            if(UPDATE_THREAD_ID == -1) {
              UPDATE_THREAD_ID = Thread.currentThread().getId();
            }
            engine.update(seconds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.f;
    }

    public static boolean isUpdateThread() {
        return Thread.currentThread().getId() == UPDATE_THREAD_ID;
    }
}
