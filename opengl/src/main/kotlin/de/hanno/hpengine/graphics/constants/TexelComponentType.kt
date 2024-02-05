package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30

val TexelComponentType.glValue: Int
    get() = when(this) {
        TexelComponentType.Float -> GL_FLOAT
        TexelComponentType.UnsignedInt -> GL_INT
        TexelComponentType.UnsignedByte -> GL_UNSIGNED_BYTE
        TexelComponentType.HalfFloat -> GL30.GL_HALF_FLOAT
        TexelComponentType.Int -> GL_INT
        TexelComponentType.Byte -> GL_BYTE
        TexelComponentType.UnsignedShort -> GL_UNSIGNED_SHORT
        TexelComponentType.UnsignedInt_10_10_10_2 -> GL_UNSIGNED_INT
        TexelComponentType.UnsignedInt_24_8 -> GL_UNSIGNED_INT
    }