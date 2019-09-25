package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.model.texture.Texture.Companion.getMipMapCountForDimension

interface TextureDimension1D {
    val width: Int

    fun getMipMapCount() = getMipMapCountForDimension(width, 0, 0)
}
interface TextureDimension2D: TextureDimension1D {
    val height: Int

    override fun getMipMapCount() = getMipMapCountForDimension(width, height, 0)
}
interface TextureDimension3D: TextureDimension2D {
    val depth: Int

    override fun getMipMapCount() = getMipMapCountForDimension(width, height, depth)
}
data class TextureDimension @JvmOverloads constructor(override val width: Int, override val height: Int = 1, override val depth: Int = 1): TextureDimension3D {
    init {
        require(width >= 1) { "width has wrong value: $width" }
        require(height >= 1) { "width has wrong value: $height" }
        require(depth >= 1) { "width has wrong value: $depth" }
    }
}