import InternalTextureFormat.*
import de.hanno.hpengine.graphics.constants.Format
import de.hanno.hpengine.graphics.constants.TexelComponentType

enum class InternalTextureFormat {
    ALPHA4,
    ALPHA8,
    ALPHA12,
    ALPHA16,
    LUMINANCE4,
    LUMINANCE8,
    LUMINANCE12,
    LUMINANCE16,
    LUMINANCE4_ALPHA4,
    LUMINANCE6_ALPHA2,
    LUMINANCE8_ALPHA8,
    LUMINANCE12_ALPHA4,
    LUMINANCE12_ALPHA12,
    LUMINANCE16_ALPHA16,
    INTENSITY,
    INTENSITY4,
    INTENSITY8,
    INTENSITY12,
    INTENSITY16,
    R3_G3_B2,
    RGB4,
    RGB5,
    RGB8,
    RGB10,
    RGB12,
    RGB16,
    RGBA2,
    RGBA4,
    RGB5_A1,
    RGBA8,
    RGB10_A2,
    RGBA12,
    RGBA16,
    TEXTURE_RED_SIZE,
    TEXTURE_GREEN_SIZE,
    TEXTURE_BLUE_SIZE,
    TEXTURE_ALPHA_SIZE,
    TEXTURE_LUMINANCE_SIZE,
    TEXTURE_INTENSITY_SIZE,
    PROXY_TEXTURE_1D,
    PROXY_TEXTURE_2D,
    RGBA32F,
    RGB32F,
    RGBA16F,
    RGB16F,
    DEPTH_COMPONENT24,
    COMPRESSED_SRGB_ALPHA_S3TC_DXT5,
    COMPRESSED_RGBA_S3TC_DXT5,
    SRGB8_ALPHA8,
    RG16F,
    R16I,
    R32F,
    DEPTH24_STENCIL8,
}

val InternalTextureFormat.format
    get() = when(this) {
        ALPHA4 -> Format.ALPHA
        ALPHA8 -> Format.ALPHA
        ALPHA12 -> Format.ALPHA
        ALPHA16 -> Format.ALPHA
        LUMINANCE4 -> Format.RGB // TODO: Is this correct?
        LUMINANCE8 -> Format.RGB // TODO: Is this correct?
        LUMINANCE12 -> Format.RGB // TODO: Is this correct?
        LUMINANCE16 -> Format.RGB // TODO: Is this correct?
        LUMINANCE4_ALPHA4 -> Format.RGBA // TODO: Is this correct?
        LUMINANCE6_ALPHA2 -> Format.RGBA // TODO: Is this correct?
        LUMINANCE8_ALPHA8 -> Format.RGBA // TODO: Is this correct?
        LUMINANCE12_ALPHA4 -> Format.RGBA // TODO: Is this correct?
        LUMINANCE12_ALPHA12 -> Format.RGBA // TODO: Is this correct?
        LUMINANCE16_ALPHA16 -> Format.RGBA // TODO: Is this correct?
        INTENSITY -> Format.RED
        INTENSITY4 -> Format.RED
        INTENSITY8 -> Format.RED
        INTENSITY12 -> Format.RED
        INTENSITY16 -> Format.RED
        R3_G3_B2 -> Format.RGB
        RGB4 -> Format.RGB
        RGB5 -> Format.RGB
        RGB8 -> Format.RGB
        RGB10 -> Format.RGB
        RGB12 -> Format.RGB
        RGB16 -> Format.RGB
        RGBA2 -> Format.RGBA
        RGBA4 -> Format.RGBA
        RGB5_A1 -> Format.RGBA
        RGBA8 -> Format.RGBA
        RGB10_A2 -> Format.RGBA
        RGBA12 -> Format.RGBA
        RGBA16 -> Format.RGBA
        TEXTURE_RED_SIZE -> Format.RED
        TEXTURE_GREEN_SIZE -> Format.GREEN
        TEXTURE_BLUE_SIZE -> Format.BLUE
        TEXTURE_ALPHA_SIZE -> Format.ALPHA
        TEXTURE_LUMINANCE_SIZE -> Format.ALPHA // TODO: is this correct?
        TEXTURE_INTENSITY_SIZE -> Format.ALPHA // TODO: is this correct?
        PROXY_TEXTURE_1D -> Format.RED // TODO: is this correct?
        PROXY_TEXTURE_2D -> Format.RED // TODO: is this correct?
        RGBA32F -> Format.RGBA
        RGB32F -> Format.RGBA
        RGBA16F -> Format.RGBA
        RGB16F -> Format.RGBA
        DEPTH_COMPONENT24 -> Format.DEPTH_COMPONENT
        COMPRESSED_SRGB_ALPHA_S3TC_DXT5 -> Format.RGB
        COMPRESSED_RGBA_S3TC_DXT5 -> Format.RGBA
        SRGB8_ALPHA8 -> Format.RGBA
        RG16F -> Format.RG
        R16I -> Format.RED_INTEGER
        R32F -> Format.RED
        DEPTH24_STENCIL8 -> Format.DEPTH_STENCIL
    }

// https://gist.github.com/Kos/4739337
val InternalTextureFormat.texelComponentType: TexelComponentType
    get() = when(this) {
        ALPHA4 -> TexelComponentType.UnsignedByte
        ALPHA8 -> TexelComponentType.UnsignedByte
        ALPHA12 -> TexelComponentType.UnsignedShort
        ALPHA16 -> TexelComponentType.UnsignedShort
        LUMINANCE4 -> TexelComponentType.UnsignedByte
        LUMINANCE8 -> TexelComponentType.UnsignedByte
        LUMINANCE12 -> TexelComponentType.UnsignedShort
        LUMINANCE16 -> TexelComponentType.UnsignedShort
        LUMINANCE4_ALPHA4 -> TexelComponentType.UnsignedByte
        LUMINANCE6_ALPHA2 -> TexelComponentType.UnsignedByte
        LUMINANCE8_ALPHA8 -> TexelComponentType.UnsignedShort
        LUMINANCE12_ALPHA4 -> TexelComponentType.UnsignedShort
        LUMINANCE12_ALPHA12 -> TexelComponentType.Float
        LUMINANCE16_ALPHA16 -> TexelComponentType.Float
        INTENSITY -> TexelComponentType.UnsignedByte
        INTENSITY4 -> TexelComponentType.UnsignedByte
        INTENSITY8 -> TexelComponentType.UnsignedByte
        INTENSITY12 -> TexelComponentType.UnsignedShort
        INTENSITY16 -> TexelComponentType.UnsignedShort
        R3_G3_B2 -> throw UnsupportedOperationException("Format unsupported: $this")
        RGB4 -> TexelComponentType.UnsignedByte
        RGB5 -> TexelComponentType.UnsignedByte
        RGB8 -> TexelComponentType.UnsignedByte
        RGB10 -> TexelComponentType.UnsignedShort
        RGB12 -> TexelComponentType.UnsignedShort
        RGB16 -> TexelComponentType.UnsignedShort
        RGBA2 -> throw UnsupportedOperationException("Format unsupported: $this")
        RGBA4 -> throw UnsupportedOperationException("Format unsupported: $this")
        RGB5_A1 -> throw UnsupportedOperationException("Format unsupported: $this")
        RGBA8 -> TexelComponentType.UnsignedByte
        RGB10_A2 -> TexelComponentType.UnsignedInt_10_10_10_2
        RGBA12 -> TexelComponentType.UnsignedShort
        RGBA16 -> TexelComponentType.UnsignedShort
        TEXTURE_RED_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        TEXTURE_GREEN_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        TEXTURE_BLUE_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        TEXTURE_ALPHA_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        TEXTURE_LUMINANCE_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        TEXTURE_INTENSITY_SIZE -> throw UnsupportedOperationException("Format unsupported: $this")
        PROXY_TEXTURE_1D -> throw UnsupportedOperationException("Format unsupported: $this")
        PROXY_TEXTURE_2D -> throw UnsupportedOperationException("Format unsupported: $this")
        RGBA32F -> TexelComponentType.Float
        RGB32F -> TexelComponentType.Float
        RGBA16F -> TexelComponentType.HalfFloat
        RGB16F -> TexelComponentType.Float
        DEPTH_COMPONENT24 -> TexelComponentType.UnsignedInt
        COMPRESSED_SRGB_ALPHA_S3TC_DXT5 -> throw UnsupportedOperationException("Format unsupported: $this")
        COMPRESSED_RGBA_S3TC_DXT5 -> throw UnsupportedOperationException("Format unsupported: $this")
        SRGB8_ALPHA8 -> TexelComponentType.UnsignedByte
        RG16F -> TexelComponentType.HalfFloat
        R16I -> TexelComponentType.Int
        R32F -> TexelComponentType.Float
        DEPTH24_STENCIL8 -> TexelComponentType.UnsignedInt_24_8
    }