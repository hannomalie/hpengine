package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

val BlendMode.mode: Int get() = when(this) {
    BlendMode.FUNC_ADD -> GL14.GL_FUNC_ADD
}

val BlendMode.Factor.glFactor: Int get() = when(this) {
    BlendMode.Factor.ZERO -> GL11.GL_ZERO
    BlendMode.Factor.ONE -> GL11.GL_ONE
    BlendMode.Factor.SRC_ALPHA -> GL11.GL_SRC_ALPHA
    BlendMode.Factor.ONE_MINUS_SRC_ALPHA -> GL11.GL_ONE_MINUS_SRC_ALPHA
}