package de.hanno.hpengine.engine.model.loader.md5

import de.hanno.hpengine.engine.Engine
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE

data class AnimationController(val maxFrames: Int, val fps: Float) {
    val animationState : AnimationState = AnimationState(maxFrames, fps)

    fun update(engine: Engine, seconds: Float) {
        if (engine.input.isKeyPressed(GLFW_KEY_SPACE)) {
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