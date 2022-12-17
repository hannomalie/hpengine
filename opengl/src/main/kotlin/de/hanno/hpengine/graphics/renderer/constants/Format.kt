package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL30.*


val Format.glValue: Int
    get() = when(this) {
        Format.RED -> GL_RED
        Format.GREEN -> GL_GREEN
        Format.BLUE -> GL_BLUE
        Format.ALPHA -> GL_ALPHA
        Format.RG -> GL_RG
        Format.RGB -> GL_RGB
        Format.RGBA -> GL_RGBA
        Format.BGR -> GL_BGR
        Format.BGRA -> GL_BGRA
        Format.RED_INTEGER -> GL_RED_INTEGER
        Format.GREEN_INTEGER -> GL_GREEN_INTEGER
        Format.BLUE_INTEGER -> GL_BLUE_INTEGER
        Format.ALPHA_INTEGER -> GL_ALPHA_INTEGER
        Format.RG_INTEGER -> GL_RG_INTEGER
        Format.RGB_INTEGER -> GL_RGB_INTEGER
        Format.RGBA_INTEGER -> GL_RGBA_INTEGER
        Format.BGR_INTEGER -> GL_BGR_INTEGER
        Format.BGRA_INTEGER -> GL_BGRA_INTEGER
        Format.STENCIL_INDEX -> GL_STENCIL_INDEX
        Format.DEPTH_COMPONENT -> GL_DEPTH_COMPONENT
        Format.DEPTH_STENCIL -> GL_DEPTH_STENCIL
    }