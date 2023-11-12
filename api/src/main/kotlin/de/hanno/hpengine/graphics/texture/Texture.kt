package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode
import java.io.File
import kotlin.math.log2
import kotlin.math.nextUp

sealed interface Texture {
    val dimension: TextureDimension
    val id: Int
    val target: TextureTarget
    val internalFormat: InternalTextureFormat
    val handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: WrapMode
    var uploadState: UploadState
    val srgba: Boolean get() = false
    fun unload() {}

    companion object {
        fun getMipMapCountForDimension(w: Int, h: Int, d: Int): Int {
            return 1 + kotlin.math.floor(log2(kotlin.math.max(w, kotlin.math.max(h, d)).toDouble())).nextUp().toInt()
        }
    }
}

interface Texture2D: Texture {
    override val dimension: TextureDimension2D
}
interface Texture3D: Texture {
    override val dimension: TextureDimension3D
}
interface CubeMap: Texture {
    override val dimension: TextureDimension2D
}
interface CubeMapArray: Texture {
    override val dimension: TextureDimension3D
}

val Texture.isMipMapped: Boolean get() = textureFilterConfig.minFilter.isMipMapped
val Texture.mipmapCount: Int get() = if(isMipMapped) { dimension.getMipMapCount() } else 0


data class FileBasedTexture2D<out T: Texture2D>(
    val path: String,
    val file: File,
    val backingTexture: T
) : Texture2D by backingTexture