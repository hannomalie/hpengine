package de.hanno.hpengine.engine.model.loader.md5

data class AnimationController(val maxFrames: Int, val fps: Float) {
    val animationState : AnimationState = AnimationState(maxFrames, fps)

    fun update(seconds: Float) {
        animationState.update(seconds)
    }

    var isHasUpdated: Boolean
        get() = animationState.isHasUpdated
        set(hasUpdated) {
            animationState.isHasUpdated = hasUpdated
        }

    val currentFrameIndex: Int
        get() = animationState.currentFrame

}