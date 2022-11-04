package de.hanno.hpengine.model.texture

import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import kotlin.math.log2
import kotlin.math.nextUp

sealed interface Texture {
    val dimension: TextureDimension
    val id: Int
    val target: TextureTarget
    val internalFormat: Int
    var handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: Int
    var uploadState: UploadState
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
