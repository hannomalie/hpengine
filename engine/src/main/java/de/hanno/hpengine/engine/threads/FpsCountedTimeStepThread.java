package de.hanno.hpengine.engine.threads;

import de.hanno.hpengine.util.fps.FPSCounter;

import java.util.concurrent.TimeUnit;

public abstract class FpsCountedTimeStepThread extends TimeStepThread {
    private FPSCounter fpsCounter = new FPSCounter();

    protected FpsCountedTimeStepThread(String name) {
        super(name);
    }

    public FpsCountedTimeStepThread(String name, float minimumCycleTimeInSeconds) {
        super(name, minimumCycleTimeInSeconds);
    }

    @Override
    public void run() {
        setName(name);

        while(!stopRequested) {
            long ns = System.nanoTime() - lastFrame;

            float seconds = (float) (ns / 1000000000.0);
            try {
                update(seconds);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            lastFrame = System.nanoTime();
            waitIfNecessary(seconds);
            fpsCounter.update();
        }
        cleanup();
    }

    public FPSCounter getFpsCounter() {
        return fpsCounter;
    }
}
