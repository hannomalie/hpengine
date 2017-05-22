package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;

public class RenderThread extends TimeStepThread {

    public RenderThread(String name) {
        super(name, 0.033f);
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
