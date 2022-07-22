package de.hanno.hpengine.graphics.buffer

import java.lang.IllegalStateException
import java.nio.ByteBuffer

interface Bufferable {
    fun putToBuffer(buffer: ByteBuffer)
    fun getFromBuffer(buffer: ByteBuffer) {
        throw IllegalStateException("Not yet implemented")
    }

    fun debugPrintFromBuffer(buffer: ByteBuffer): String? {
        throw IllegalStateException("Not yet implemented")
    }

    val bytesPerObject: Int

    /**
     * Populates this instance with data from the given buffer.
     * This only works with Bufferables with fixed byte size, because
     * byte size multiplied with index i is the used byte offset.
     * @param i the index the data should be retrieved from
     * @param buffer the buffer the data should be retrieved from
     */
    fun getFromIndex(i: Int, buffer: ByteBuffer) {
        buffer.position(i * bytesPerObject)
        getFromBuffer(buffer)
    }
}