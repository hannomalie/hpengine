package de.hanno.hpengine.model

import EntityStruktImpl.Companion.type
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.buffers.copyTo
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.typed

class EntityBuffer(val underlying: TypedBuffer<EntityStrukt> = BufferUtils.createByteBuffer(EntityStrukt.type.sizeInBytes).typed(
    EntityStrukt.type))

fun <T> TypedBuffer<T>.enlarge(size: Int, copyContent: Boolean = true, rewindBuffers: Boolean = true) = enlargeToBytes(size * struktType.sizeInBytes, copyContent, rewindBuffers)

fun <T> TypedBuffer<T>.enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true, rewindBuffers: Boolean = true) = if(byteBuffer.capacity() < sizeInBytes) {
    TypedBuffer(BufferUtils.createByteBuffer(sizeInBytes), struktType).apply {
        if(copyContent) {
            val self = this@apply
            this@enlargeToBytes.byteBuffer.copyTo(self.byteBuffer, rewindBuffers = rewindBuffers)
        }
    }
} else this

