package de.hanno.hpengine.model.animation

data class AnimationController(val animations: Map<String, Animation>) {
    fun update(seconds: Float) {
        animations.forEach { (_, animation) ->
            animation.update(seconds)
        }
    }
}
