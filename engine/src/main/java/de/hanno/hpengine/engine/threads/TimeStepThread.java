package de.hanno.hpengine.engine.threads;

import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public abstract class TimeStepThread extends Thread {
    protected final String name;
    private Logger LOGGER;

    private long start = 0l;
    protected long lastFrame = 0l;

    public boolean stopRequested = false;
    protected volatile float minimumCycleTimeInSeconds;

    //TODO: Consider this public
    protected TimeStepThread(String name) {
        this(name, 0.0f);
    }
    public TimeStepThread(String name, float minimumCycleTimeInSeconds) {
        super();
        LOGGER = LogManager.getLogManager().getLogger(TimeStepThread.class.getName() + " " + name);
        setMinimumCycleTimeInSeconds(minimumCycleTimeInSeconds);
        this.name = name;
        setUncaughtExceptionHandler((t, e) -> {
            LOGGER.severe("An error!");
            e.printStackTrace();
        });
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
        setName(name);

        while(!stopRequested) {
            long ns = System.nanoTime() - lastFrame;

            float seconds = TimeUnit.NANOSECONDS.toSeconds(ns);
            update(seconds);
            waitIfNecessary(seconds);
            lastFrame = System.nanoTime();
        }
        cleanup();
    }

    protected void waitIfNecessary(float actualS) {
        float secondsLeft = (getMinimumCycleTimeInSeconds() - actualS);
        if(secondsLeft <= 0) { return; }

        while(secondsLeft >= 0) {
            try {
                long timeBeforeSleep = System.nanoTime();
                Thread.sleep(0, 100000);
                long sleptNanoSeconds = System.nanoTime() - timeBeforeSleep;
                double sleptSeconds = sleptNanoSeconds / 1000000000.0;
                secondsLeft -= sleptSeconds;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public void cleanup() {

    }

    public abstract void update(float seconds);

    private void setMinimumCycleTimeInSeconds(float minimumCycleTimeInSeconds) {
        this.minimumCycleTimeInSeconds = minimumCycleTimeInSeconds;
    }

    public float getMinimumCycleTimeInSeconds() {
        return minimumCycleTimeInSeconds;
    }
}
