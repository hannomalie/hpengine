package de.hanno.hpengine.engine.model.texture

interface TextureDimension1D {
    val width: Int
}
interface TextureDimension2D: TextureDimension1D {
    val height: Int
}
interface TextureDimension3D: TextureDimension2D {
    val depth: Int
}
data class TextureDimension @JvmOverloads constructor(override val width: Int, override val height: Int = 0, override val depth: Int = 0): TextureDimension3D