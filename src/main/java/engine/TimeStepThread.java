package engine;

import renderer.Renderer;
import renderer.fps.FPSCounter;

public abstract class TimeStepThread extends Thread {

    private long start = 0l;
    private long lastFrame = 0l;
    private FPSCounter fpsCounter = new FPSCounter();

    public boolean stopRequested = false;
    private volatile float minimumCycleTimeInSeconds;

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

            float seconds = ns / 1000f / 1000f / 1000f;
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
        float secondsLeft = (minimumCycleTimeInSeconds - actualS);
        if(secondsLeft <= 0) { return; }

        long nanoSecondsLeft = (long) (secondsLeft * 1000 * 1000 * 1000);
        long startTime = System.nanoTime();
        long targetTime = (startTime + nanoSecondsLeft);
        while(System.nanoTime() < targetTime) {
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
