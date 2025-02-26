package de.hanno.hpengine.graphics.buffer

import java.nio.ByteBuffer

data class BufferDefinition(val id: Int, val buffer: ByteBuffer, val isImmutable: Boolean) {
    init {
        require(id > 0) { "Buffer id is invalid: $id" }
    }
}