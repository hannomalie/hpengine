package de.hanno.hpengine.buffers

import java.nio.ByteBuffer

fun ByteBuffer.copyTo(target: ByteBuffer, rewindBuffers: Boolean = true, targetOffset: Int = 0) {
    require(target !== this) { throw IllegalStateException("Cannot copy from and into the same buffer") }

    val positionBefore = position()
    val targetPositionBefore = target.position()
    if(rewindBuffers) {
        rewind()
        target.rewind()
    }
    val targetBufferSmallerThanNeeded = capacity() > target.capacity() - targetOffset

    if(targetBufferSmallerThanNeeded) {
        val array = toArray(true, target.capacity())
        target.put(array, targetOffset, array.size-1)
        target.rewind()
    } else {
        if(positionBefore != targetOffset) { target.position(targetOffset) }
        target.put(this)
    }
    if(rewindBuffers) {
        rewind()
        target.rewind()
    } else {
        position(positionBefore)
        target.position(targetPositionBefore)
    }
}

fun ByteBuffer.toArray(rewindBuffer: Boolean = true, sizeInBytes: Int = capacity()): ByteArray {
    val positionBefore = position()
    return if(rewindBuffer) {
        rewind()
        ByteArray(sizeInBytes).apply {
            get(this, 0, this.size)
        }
    } else {
        ByteArray(Math.max(sizeInBytes, remaining())).apply {
            get(this, position(), this.size)
        }
    }.apply {
        if(rewindBuffer) {
            rewind()
        } else {
            position(positionBefore)
        }
    }
}