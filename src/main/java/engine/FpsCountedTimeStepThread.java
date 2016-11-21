package engine;

import renderer.fps.FPSCounter;

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

            float seconds = ns / 1000f / 1000f / 1000f;
//            try {
            update(seconds);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
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
