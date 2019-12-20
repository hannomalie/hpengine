package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.model.texture.Texture.Companion.getMipMapCountForDimension

sealed class TextureDimension {
    abstract fun getMipMapCount(): Int
    companion object {
        operator fun invoke(width: Int) = TextureDimension1D(width)
        operator fun invoke(width: Int, height: Int) = TextureDimension2D(width, height)
        operator fun invoke(width: Int, height: Int, depth: Int) = TextureDimension3D(width, height, depth)
    }
}
class TextureDimension1D(val width: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
    }
    override fun getMipMapCount() = getMipMapCountForDimension(width, 0, 0)
}
class TextureDimension2D(val width: Int, val height: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
        require(height >= 1) { "width has wrong value: $height" }
    }
    override fun getMipMapCount() = getMipMapCountForDimension(width, height, 0)
}
class TextureDimension3D(val width: Int, val height: Int, val depth: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
        require(height >= 1) { "width has wrong value: $height" }
        require(depth >= 1) { "width has wrong value: $depth" }
    }
    override fun getMipMapCount() = getMipMapCountForDimension(width, height, depth)
}
