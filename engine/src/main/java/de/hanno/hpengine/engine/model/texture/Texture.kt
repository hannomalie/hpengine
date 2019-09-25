package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.GL11
import java.lang.Math.floor
import java.lang.Math.max
import kotlin.math.log2
import kotlin.math.nextUp

interface Texture<out DIMENSION: TextureDimension1D> {
    val dimension: DIMENSION
    val id: Int
    val target: GlTextureTarget
    val internalFormat: Int
    var handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: Int
    var uploadState: UploadState
    fun unload() {}

    companion object {
        fun getMipMapCountForDimension(w: Int, h: Int, d: Int): Int {
            return 1 + floor(log2(max(w, max(h, d)).toDouble())).nextUp().toInt()
        }
    }
}


val Texture<*>.isMipMapped: Boolean
    get() = textureFilterConfig.minFilter.isMipMapped

//TODO: Remove this and all usages, convert to property above
val Int.isMipMapped: Boolean
    get() {
        return this == GL11.GL_LINEAR_MIPMAP_LINEAR ||
                this == GL11.GL_LINEAR_MIPMAP_NEAREST ||
                this == GL11.GL_NEAREST_MIPMAP_LINEAR ||
                this == GL11.GL_NEAREST_MIPMAP_NEAREST
    }


val Texture<TextureDimension3D>.mipmapCount: Int
    get() = if(isMipMapped) {
            Util.calculateMipMapCount(dimension.width, dimension.height) // TODO: Consider depth parameter
        } else 0