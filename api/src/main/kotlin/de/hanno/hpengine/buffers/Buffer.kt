package de.hanno.hpengine.buffers

import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.TypedBuffer
import java.nio.ByteBuffer

interface Buffer {
    val buffer: ByteBuffer
}

fun <T: Strukt> TypedBuffer<T>.enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(
    size * struktType.sizeInBytes,
    copyContent
)

fun <T: Strukt> TypedBuffer<T>.enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) = if(byteBuffer.capacity() < sizeInBytes) {
    TypedBuffer(BufferUtils.createByteBuffer(sizeInBytes), struktType).apply {
        if(copyContent) {
            this@enlargeToBytes.byteBuffer.copyTo(this@apply.byteBuffer)
        }
    }
} else this
