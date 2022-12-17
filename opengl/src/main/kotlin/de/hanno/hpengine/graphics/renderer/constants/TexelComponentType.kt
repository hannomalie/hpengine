package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11.*

val TexelComponentType.glValue: Int
    get() = when(this) {
        TexelComponentType.Float -> GL_FLOAT
        TexelComponentType.Int -> GL_INT
        TexelComponentType.UnsignedByte -> GL_UNSIGNED_BYTE
    }