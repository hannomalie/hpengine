package de.hanno.hpengine.engine.model.loader.md5;

public class AnimationState {
    private final float fps;
    private final float spf;
    private int maxFrames;
    private int currentFrame;
    private int lastFrame;
    private boolean hasUpdated = false;
    private float currentSeconds = 0;

    public AnimationState(int maxFrames, float fps) {
        this.maxFrames = maxFrames;
        this.fps = fps;
        this.spf = 1f/fps;
    }

    public void nextFrame() {
        int nextFrame = currentFrame + 1;
        if (nextFrame > maxFrames - 1) {
            currentFrame = 0;
        } else {
            currentFrame = nextFrame;
        }
        setHasUpdated(true);
    }

    public boolean isHasUpdated() {
        hasUpdated = !(lastFrame == currentFrame);
        return hasUpdated;
    }

    public void setHasUpdated(boolean hasUpdated) {
        if (this.hasUpdated) {
            lastFrame = currentFrame;
        }
        this.hasUpdated = hasUpdated;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void update(float seconds) {
        currentSeconds += seconds;
        if(currentSeconds > spf) {
            currentSeconds -= spf;
            nextFrame();
        }
    }
}