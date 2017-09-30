package de.hanno.hpengine.engine.model.loader.md5;

public class AnimationController {
    final AnimationState animationState = new AnimationState();

    public AnimationController(int maxFrames) {
        this.animationState.maxFrames = maxFrames;
    }

    public void nextFrame() {
        animationState.nextFrame();
    }

    public boolean isHasUpdated() {
        return animationState.isHasUpdated();
    }
    public void setHasUpdated(boolean hasUpdated) {
        animationState.setHasUpdated(hasUpdated);
    }

    public int getCurrentFrameIndex() {
        return animationState.currentFrame;
    }

}