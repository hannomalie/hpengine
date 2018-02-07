package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;

public class UpdateThread extends FpsCountedTimeStepThread {

    private Engine engine;

    public UpdateThread(Engine engine, String name, float minCycleTimeInS) {
        super(name, minCycleTimeInS);
        this.engine = engine;
    }

    @Override
    public void update(float seconds) {
        try {
            engine.update(seconds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockUpdaterate() ? minimumCycleTimeInSeconds : 0.f;
    }
}
