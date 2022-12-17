package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.renderer.constants.WrapMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TextureAllocationData(
    val textureId: Int,
    val handle: Long,
    val wrapMode: WrapMode
)

enum class CubeMapFileDataFormat {
    Single,
    Six
}

fun ByteBuffer.buffer(values: ByteArray) {
    order(ByteOrder.nativeOrder())
    put(values, 0, values.size)
    flip()
}