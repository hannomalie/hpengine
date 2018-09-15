package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL11

interface Texture {
    val width: Int
    val height: Int
    val depth: Int
    val textureId: Int
    val target: GlTextureTarget
    val internalFormat: Int
    var handle: Long
    val minFilter: Int
    val magFilter: Int
    val wrapMode: Int
    var uploadState: UploadState
    fun unload() {}
}


val Texture.isMipMapped: Boolean
    get() {
        return magFilter == GL11.GL_LINEAR_MIPMAP_LINEAR ||
                magFilter == GL11.GL_LINEAR_MIPMAP_NEAREST ||
                magFilter == GL11.GL_NEAREST_MIPMAP_LINEAR ||
                magFilter == GL11.GL_NEAREST_MIPMAP_NEAREST
    }

//TODO: Remove this and all usages, convert to property above
val Int.isMipMapped: Boolean
    get() {
        return this == GL11.GL_LINEAR_MIPMAP_LINEAR ||
                this == GL11.GL_LINEAR_MIPMAP_NEAREST ||
                this == GL11.GL_NEAREST_MIPMAP_LINEAR ||
                this == GL11.GL_NEAREST_MIPMAP_NEAREST
    }


val Texture.mipmapCount: Int
    get() = if(isMipMapped) {
            Util.calculateMipMapCount(width, height)
        } else 0