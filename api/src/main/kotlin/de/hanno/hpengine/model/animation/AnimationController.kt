package de.hanno.hpengine.model.animation

import de.hanno.hpengine.config.getValue
import de.hanno.hpengine.config.setValue

data class AnimationController(val animation : Animation) {

    fun update(seconds: Float) {
        animation.update(seconds)
    }

    var wasUpdated by animation::hasUpdated
    var fps by animation::fps

    val currentFrameIndex: Int
        get() = animation.currentFrame

}