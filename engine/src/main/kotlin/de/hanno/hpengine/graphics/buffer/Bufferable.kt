package de.hanno.hpengine.graphics.buffer

import java.lang.IllegalStateException
import java.nio.ByteBuffer

interface Bufferable {
    fun putToBuffer(buffer: ByteBuffer)

    fun debugPrintFromBuffer(buffer: ByteBuffer): String? {
        throw IllegalStateException("Not yet implemented")
    }

    val bytesPerObject: Int
}