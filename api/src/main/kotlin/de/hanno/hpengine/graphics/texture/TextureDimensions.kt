package de.hanno.hpengine.graphics.texture

sealed class TextureDimension {
    abstract val mipMapCount: Int
    companion object {
        operator fun invoke(width: Int) = TextureDimension1D(width)
        operator fun invoke(width: Int, height: Int) = TextureDimension2D(width, height)
        operator fun invoke(width: Int, height: Int, depth: Int) = TextureDimension3D(width, height, depth)
    }
}
data class TextureDimension1D(val width: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
    }

    override val mipMapCount get() = getMipMapCountForDimension(width)
}
data class TextureDimension2D(val width: Int, val height: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
        require(height >= 1) { "height has wrong value: $height" }
    }

    override val mipMapCount get() = getMipMapCountForDimension(width, height)
}
data class TextureDimension3D(val width: Int, val height: Int, val depth: Int): TextureDimension() {
    init {
        require(width >= 1) { "width has wrong value: $width" }
        require(height >= 1) { "height has wrong value: $height" }
        require(depth >= 1) { "depth has wrong value: $depth" }
    }

    override val mipMapCount get() = getMipMapCountForDimension(width, height, depth)
}
