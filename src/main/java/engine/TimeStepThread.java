package engine;

import renderer.fps.FPSCounter;

public abstract class TimeStepThread extends Thread {

    private long start = 0l;
    private long lastFrame = 0l;
    private FPSCounter fpsCounter = new FPSCounter();

    public boolean stopRequested = false;
    private float minimumCycleTimeInSeconds;

    //TODO: Consider this public
    private TimeStepThread(String name) {
        this(name, 0.0f);
    }
    public TimeStepThread(String name, float minimumCycleTimeInSeconds) {
        super();
        setMinimumCycleTimeInSeconds(minimumCycleTimeInSeconds);
        setName(name);
    }

    @Override
    public void start() {
        setDaemon(true);
        super.start();
        start = System.nanoTime();
        lastFrame = System.nanoTime();
    }
    @Override
    public void run() {

        while(!stopRequested) {
            long ns = System.nanoTime() - lastFrame;

            float seconds = ((float) ns) / 1000000f;
            try {
                update(seconds);

            }catch (Exception e) {
                e.printStackTrace();
            }
            lastFrame = System.nanoTime();
            waitIfNecessary(seconds);
            fpsCounter.update();
        }
        cleanup();
    }

    private void waitIfNecessary(float actualS) {
        float newActualS = actualS;
        while(newActualS < minimumCycleTimeInSeconds) {
            float step = 0.001f;
            newActualS += step;
        }
    }

    public FPSCounter getFpsCounter() {
        return fpsCounter;
    }

    public void cleanup() {

    }

    public abstract void update(float seconds);

    public void setMinimumCycleTimeInSeconds(float minimumCycleTimeInSeconds) {
        this.minimumCycleTimeInSeconds = minimumCycleTimeInSeconds;
    }
}
