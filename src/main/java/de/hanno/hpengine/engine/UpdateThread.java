package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;

class UpdateThread extends FpsCountedTimeStepThread {

    public UpdateThread(String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
    }

    @Override
    public void update(float seconds) {
        Engine.getInstance().update(seconds > 0.005f ? seconds : 0.0001f);
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.0001f;
    }
}