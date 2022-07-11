package de.hanno.hpengine.engine.graphics.renderer.constants

import org.lwjgl.opengl.GL11.*

data class TextureFilterConfig @JvmOverloads constructor(
    val minFilter: MinFilter = MinFilter.LINEAR_MIPMAP_LINEAR,
    val magFilter: MagFilter = MagFilter.LINEAR
)

enum class MinFilter(val glValue: Int, val isMipMapped: Boolean = false) {
    NEAREST(GL_NEAREST),
    LINEAR(GL_LINEAR),
    NEAREST_MIPMAP_NEAREST(GL_NEAREST_MIPMAP_NEAREST, true),
    LINEAR_MIPMAP_NEAREST(GL_LINEAR_MIPMAP_NEAREST, true),
    NEAREST_MIPMAP_LINEAR(GL_NEAREST_MIPMAP_LINEAR, true),
    LINEAR_MIPMAP_LINEAR(GL_LINEAR_MIPMAP_LINEAR, true)
}

enum class MagFilter(val glValue: Int) {
    NEAREST(GL_NEAREST),
    LINEAR(GL_LINEAR)
}
