package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.constants.Format
import java.util.concurrent.Future

data class TextureInfo(
    val srgba: Boolean,
    val width: Int,
    val height: Int,
    val mipMapCount: Int,
    val srcPixelFormat: Format,
    val mipmapsGenerated: Boolean,
    val sourceDataCompressed: Boolean,
    val hasAlpha: Boolean)

data class CompleteTextureInfo(val info: TextureInfo, val data: Array<Future<ByteArray>>) {
    val hasAlpha: Boolean = info.hasAlpha
}
