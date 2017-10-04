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

        long currentTimeNs = System.nanoTime();
        double dtS = 1 / 60.0;

        while(!stopRequested) {

            long newTimeNs = System.nanoTime();
            double frameTimeNs = (newTimeNs - currentTimeNs);

            double frameTimeS = frameTimeNs / 1000000000.0d;
            currentTimeNs = newTimeNs;

            while(frameTimeS > 0.0)
            {
                double deltaTime = Math.min(frameTimeS, dtS);
                update((float) deltaTime);
                fpsCounter.update();
                frameTimeS -= deltaTime;
            }

        }
        cleanup();
    }

    public FPSCounter getFpsCounter() {
        return fpsCounter;
    }
}
