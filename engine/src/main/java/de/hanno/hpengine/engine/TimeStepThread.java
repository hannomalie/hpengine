package de.hanno.hpengine.engine;

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

            float seconds = ns / 1000 / 1000;//TimeUnit.NANOSECONDS.convert(ns, TimeUnit.SECONDS);
            update(seconds);
            lastFrame = System.nanoTime();
            waitIfNecessary(seconds);
        }
        cleanup();
    }

    protected void waitIfNecessary(float actualS) {
        try {
            Thread.sleep(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        float secondsLeft = (getMinimumCycleTimeInSeconds() - actualS);
        if(secondsLeft <= 0) { return; }

        boolean moreThanThreeMsToWait = secondsLeft >= 0.003;
        if(moreThanThreeMsToWait) {
            try {
                long timeBeforeSleep = System.nanoTime();
                Thread.sleep(0, 500);
                long sleptNanoSeconds = System.nanoTime() - timeBeforeSleep;
                long sleptMs = sleptNanoSeconds / 1000 / 1000;
                secondsLeft -= sleptMs/1000f;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        long nanoSecondsLeft = (long) (secondsLeft * 1000 * 1000 * 1000);
        long startTime = System.nanoTime();
        long targetTime = (startTime + nanoSecondsLeft);
        while(System.nanoTime() < targetTime) {
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
