package de.hanno.hpengine.engine.model

class Animation(val name: String,
                val frames: List<AnimatedFrame>,
                val duration: Float,
                var fps: Float) {

    private val maxFrames = frames.size
    private val spf: Float
        get() = 1f / fps

    var currentFrame = 0
        private set

    private var lastFrame = 0

    var hasUpdated = false
        get() {
            field = lastFrame != currentFrame
            return field
        }
        set(hasUpdated) {
            if (this.hasUpdated) {
                lastFrame = currentFrame
            }
            field = hasUpdated
        }

    private var currentSeconds = 0f
    fun nextFrame() {
        val nextFrame = currentFrame + 1
        currentFrame = if (nextFrame > maxFrames - 1) {
            0
        } else {
            nextFrame
        }
        hasUpdated = true
    }

    fun update(seconds: Float) {
        currentSeconds += seconds
        if (currentSeconds > spf) {
            currentSeconds -= spf
            nextFrame()
        }
    }

}