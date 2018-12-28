package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;

public class RenderThread extends TimeStepThread {

    private final Runnable action;

    public RenderThread(String name, Runnable action) {
        super(name, 0.016f);
        this.action = action;
    }

    @Override
    public void update(float seconds) {
        action.run();
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockFps() ? minimumCycleTimeInSeconds : 0f;
    }
}
