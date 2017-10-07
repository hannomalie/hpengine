package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;

public class UpdateThread extends FpsCountedTimeStepThread {

    public UpdateThread(String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
    }

    @Override
    public void update(float seconds) {
        try {
            Engine.getInstance().update(seconds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.f;
    }
}
