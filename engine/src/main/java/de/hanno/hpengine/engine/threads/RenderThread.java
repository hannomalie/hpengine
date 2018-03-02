package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;

public class RenderThread extends TimeStepThread {

    private Engine engine;

    public RenderThread(Engine engine, String name) {
        super(name, 0.016f);
        this.engine = engine;
    }

    @Override
    public void update(float seconds) {
        engine.actuallyDraw();
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockFps() ? minimumCycleTimeInSeconds : 0f;
    }
}
