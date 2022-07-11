package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import java.lang.Math.floor
import java.lang.Math.max
import kotlin.math.log2
import kotlin.math.nextUp

interface Texture {
    val dimension: TextureDimension
    val id: Int
    val target: GlTextureTarget
    val internalFormat: Int
    var handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: Int
    var uploadState: UploadState
    fun unload() {}
    val isMipMapped: Boolean
        get() = textureFilterConfig.minFilter.isMipMapped

    companion object {
        fun getMipMapCountForDimension(w: Int, h: Int, d: Int): Int {
            return 1 + floor(log2(max(w, max(h, d)).toDouble())).nextUp().toInt()
        }
    }
}
interface ICubeMap: Texture


////TODO: Remove this and all usages, convert to property above
//val Int.isMipMapped: Boolean
//    get() {
//        return this == GL11.GL_LINEAR_MIPMAP_LINEAR ||
//                this == GL11.GL_LINEAR_MIPMAP_NEAREST ||
//                this == GL11.GL_NEAREST_MIPMAP_LINEAR ||
//                this == GL11.GL_NEAREST_MIPMAP_NEAREST
//    }


val Texture.mipmapCount: Int
    get() = if(isMipMapped) { dimension.getMipMapCount() } else 0