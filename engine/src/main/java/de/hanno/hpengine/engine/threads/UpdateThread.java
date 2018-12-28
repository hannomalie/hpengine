package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;

import java.util.function.Consumer;

public class UpdateThread extends FpsCountedTimeStepThread {
    private static long UPDATE_THREAD_ID = -1;
    private final Consumer<Float> action;


    public UpdateThread(Consumer<Float> action, String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
        this.action = action;
    }

    @Override
    public void update(float seconds) {
        try {
            if(UPDATE_THREAD_ID == -1) {
              UPDATE_THREAD_ID = Thread.currentThread().getId();
            }
            action.accept(seconds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.f;
    }

    public static boolean isUpdateThread() {
        return Thread.currentThread().getId() == UPDATE_THREAD_ID || Thread.currentThread().getName().equals("main"); //TODO: This is a very ugly hack, please remove this somehow
    }
}
