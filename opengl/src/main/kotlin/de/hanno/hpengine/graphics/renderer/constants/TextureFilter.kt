package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11

val MinFilter.glValue: Int
    get() = when(this) {
        MinFilter.NEAREST -> GL11.GL_NEAREST
        MinFilter.LINEAR -> GL11.GL_LINEAR
        MinFilter.NEAREST_MIPMAP_NEAREST -> GL11.GL_NEAREST_MIPMAP_NEAREST
        MinFilter.LINEAR_MIPMAP_NEAREST -> GL11.GL_LINEAR_MIPMAP_NEAREST
        MinFilter.NEAREST_MIPMAP_LINEAR -> GL11.GL_NEAREST_MIPMAP_LINEAR
        MinFilter.LINEAR_MIPMAP_LINEAR -> GL11.GL_LINEAR_MIPMAP_LINEAR
    }

val MagFilter.glValue: Int
    get() = when(this) {
        MagFilter.NEAREST -> GL11.GL_NEAREST
        MagFilter.LINEAR -> GL11.GL_LINEAR
    }