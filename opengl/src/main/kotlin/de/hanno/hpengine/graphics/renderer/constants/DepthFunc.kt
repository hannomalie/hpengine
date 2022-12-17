package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11

val DepthFunc.glFunc: Int get() = when(this) {
    DepthFunc.LESS -> GL11.GL_LESS
    DepthFunc.LEQUAL -> GL11.GL_LEQUAL
    DepthFunc.EQUAL -> GL11.GL_EQUAL
    DepthFunc.GREATER -> GL11.GL_GREATER
}