package engine;

public abstract class TimeStepThread extends Thread {

    private long start = 0l;
    private long lastFrame = 0l;

    public TimeStepThread(String name) {
        super();
        setName(name);
    }

    @Override
    public void start() {
        setDaemon(true);
        super.start();
        start = System.currentTimeMillis();
        lastFrame = System.currentTimeMillis();
    }
    public void run() {
        long ms = System.currentTimeMillis() - lastFrame;
        update(ms / 1000f);
        lastFrame = System.currentTimeMillis();
    }

    public abstract void update(float seconds);
}
