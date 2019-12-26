package de.hanno.hpengine.engine.model.animation

import de.hanno.hpengine.engine.config.getValue
import de.hanno.hpengine.engine.config.setValue

data class AnimationController(val animation : Animation) {

    fun update(seconds: Float) {
        animation.update(seconds)
    }

    var isHasUpdated by animation::hasUpdated
    var fps by animation::fps

    val currentFrameIndex: Int
        get() = animation.currentFrame

}