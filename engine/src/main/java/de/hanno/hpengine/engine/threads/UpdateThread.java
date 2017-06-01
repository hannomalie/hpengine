package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;

public class UpdateThread extends FpsCountedTimeStepThread {

    private static final float MINIMUM_UPDATE_SECONDS = 0.0005f;

    public UpdateThread(String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
    }

    @Override
    public void update(float seconds) {
        Engine.getInstance().update(seconds > MINIMUM_UPDATE_SECONDS ? seconds : MINIMUM_UPDATE_SECONDS);
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.f;
    }
}
