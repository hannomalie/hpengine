package de.hanno.hpengine.buffers

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.position
import java.nio.ByteBuffer
import java.nio.FloatBuffer

fun ByteBuffer.copyTo(
    target: ByteBuffer,
    targetOffsetInBytes: SizeInBytes = SizeInBytes(0),
) {
    require(target !== this) { "Cannot copy from and into the same buffer" }

    rewind()
    target.rewind()
    val requiredSize = SizeInBytes(capacity())
    val availableBytes = SizeInBytes(target.capacity()) - targetOffsetInBytes
    val targetBufferSmallerThanNeeded = requiredSize > availableBytes

    require(!targetBufferSmallerThanNeeded) {
        "Target buffer too small, resize before! requiredSizeInBytes: $requiredSize but got $availableBytes at offset $targetOffsetInBytes"
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