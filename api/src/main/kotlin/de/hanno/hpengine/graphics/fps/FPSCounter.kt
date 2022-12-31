package de.hanno.hpengine.graphics.fps

class CPSCounter: FPSCounter()

open class FPSCounter {
    val stack = longArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    var currentIndex = 0

    fun update() {
        push(nanoTime)

        val diffInNanosForNFrames = last - longestAgo
        msPerFrame = diffInNanosForNFrames / stack.size / 1000000.0f
    }

    val fps: Float
        get() = 1000f / msPerFrame

    var msPerFrame = 0f
        private set

    private fun push(value: Long) {
        stack[currentIndex] = value
        currentIndex = nextIndex
    }

    val last: Long
        get() = stack[if (currentIndex - 1 < 0) stack.size - 1 else currentIndex - 1]

    val longestAgo: Long
        get() = stack[nextIndex]

    private inline val nextIndex get() = if (isAtStackEnd) 0 else currentIndex + 1
    private inline val isAtStackEnd get() = currentIndex + 1 > stack.size - 1

    val current: Long
        get() = stack[currentIndex]
}

private inline val nanoTime: Long get() = System.nanoTime()