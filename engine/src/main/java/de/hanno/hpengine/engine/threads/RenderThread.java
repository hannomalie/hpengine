package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;

public class RenderThread extends TimeStepThread {

    public RenderThread(String name) {
        super(name, 0.016f);
    }

    @Override
    public void update(float seconds) {
        if (Config.getInstance().isMultithreadedRendering()) {
            try {
                Engine.getInstance().actuallyDraw();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Config.getInstance().isLockFps() ? minimumCycleTimeInSeconds : 0f;
    }
}
