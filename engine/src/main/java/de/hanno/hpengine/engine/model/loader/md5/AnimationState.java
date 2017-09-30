package de.hanno.hpengine.engine.model.loader.md5;

public class AnimationState {
    int maxFrames;
    int currentFrame;
    int lastFrame;
    boolean hasUpdated = false;

    public AnimationState() {
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
}