package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;

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
