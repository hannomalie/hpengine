package de.hanno.hpengine.engine.model.loader.md5

import de.hanno.hpengine.engine.input.Input
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE

data class AnimationController(val maxFrames: Int, val fps: Float) {
    val animationState : AnimationState = AnimationState(maxFrames, fps)

    fun update(seconds: Float) {
        if (Input.isKeyPressed(GLFW_KEY_SPACE)) {
            animationState.update(seconds)
        }
    }

    var isHasUpdated: Boolean
        get() = animationState.isHasUpdated
        set(hasUpdated) {
            animationState.isHasUpdated = hasUpdated
        }

    val currentFrameIndex: Int
        get() = animationState.currentFrame

}