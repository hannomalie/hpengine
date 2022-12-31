package de.hanno.hpengine.buffers

import java.nio.ByteBuffer
import java.nio.FloatBuffer

fun ByteBuffer.copyTo(
    target: ByteBuffer,
    targetOffsetInBytes: Int = 0,
) {
    require(target !== this) { "Cannot copy from and into the same buffer" }

    rewind()
    target.rewind()
    val requiredSizeInBytes = capacity()
    val targetBufferSmallerThanNeeded = requiredSizeInBytes > target.capacity() - targetOffsetInBytes

    require(!targetBufferSmallerThanNeeded) {
        "Target buffer too small, resize before!"
    }
    target.position(targetOffsetInBytes)
    target.put(this)

    rewind()
    target.rewind()
}

fun FloatBuffer.safePut(matrix: FloatBuffer) {
    rewind()
    put(matrix)
    rewind()
    matrix.rewind()
}