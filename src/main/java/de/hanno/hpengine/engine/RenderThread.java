package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;

class RenderThread extends TimeStepThread {

    public RenderThread(String name) {
        super(name, 0.033f);
    }

    @Override
    public void update(float seconds) {
        if (Config.MULTITHREADED_RENDERING) {
            try {
                Engine.getInstance().actuallyDraw();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return 0;
    }
}
