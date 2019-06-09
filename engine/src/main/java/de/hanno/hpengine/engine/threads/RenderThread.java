package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;

public class RenderThread extends TimeStepThread {

    private final Runnable action;
    private final Config config;

    public RenderThread(String name, Runnable action, Config config) {
        super(name, 0.016f);
        this.action = action;
        this.config = config;
    }

    @Override
    public void update(float seconds) {
        action.run();
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return config.isLockFps() ? minimumCycleTimeInSeconds : 0f;
    }
}
