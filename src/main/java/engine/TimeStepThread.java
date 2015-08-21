package engine;

public abstract class TimeStepThread extends Thread {

    private long start = 0l;
    private long lastFrame = 0l;

    public boolean stopRequested = false;

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
    @Override
    public void run() {

        while(!stopRequested) {
            long ms = System.currentTimeMillis() - lastFrame;
            try {
                update(ms / 1000f);
            }catch (Exception e) {
                e.printStackTrace();
            }
            lastFrame = System.currentTimeMillis();
        }
        cleanup();
    }

    public void cleanup() {

    }

    public abstract void update(float seconds);
}
