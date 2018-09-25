package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig.MinFilter
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.GL11

interface Texture<out DIMENSION: TextureDimension1D> {
    val dimension: DIMENSION
    val textureId: Int
    val target: GlTextureTarget
    val internalFormat: Int
    var handle: Long
    val textureFilterConfig: TextureFilterConfig
    val wrapMode: Int
    var uploadState: UploadState
    fun unload() {}
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