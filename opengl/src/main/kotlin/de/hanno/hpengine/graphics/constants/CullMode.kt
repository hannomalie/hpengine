package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.GL11

val CullMode.glMode: Int get() = when(this) {
    CullMode.FRONT -> GL11.GL_FRONT
    CullMode.BACK -> GL11.GL_BACK
}