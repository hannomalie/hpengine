package de.hanno.hpengine.engine.model.texture

import java.util.concurrent.Future

data class TextureInfo(val srgba: Boolean,
                       val width: Int,
                       val height: Int,
                       val mipMapCount: Int,
                       val srcPixelFormat: Int,
                       val mipmapsGenerated: Boolean,
                       val sourceDataCompressed: Boolean)

data class CompleteTextureInfo(val info: TextureInfo, val data: Array<Future<ByteArray>>)
