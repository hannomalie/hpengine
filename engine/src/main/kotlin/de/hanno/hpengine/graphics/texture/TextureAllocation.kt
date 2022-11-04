package de.hanno.hpengine.graphics.texture

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class CubeMapFileDataFormat {
    Single,
    Six
}

fun ByteBuffer.buffer(values: ByteArray) {
    order(ByteOrder.nativeOrder())
    put(values, 0, values.size)
    flip()
}
