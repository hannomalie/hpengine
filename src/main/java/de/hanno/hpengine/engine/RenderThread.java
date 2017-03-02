package de.hanno.hpengine.engine;

public class RenderThread extends TimeStepThread {

    public RenderThread(String name) {
        super(name, 0.033f);
    }

    @Override
    public void update(float seconds) {
        if (Engine.getInstance().getConfig().isMultithreadedRendering()) {
            try {
                Engine.getInstance().actuallyDraw();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public float getMinimumCycleTimeInSeconds() {
        return Engine.getInstance().getConfig().isLockFps() ? minimumCycleTimeInSeconds : 0f;
    }
}
