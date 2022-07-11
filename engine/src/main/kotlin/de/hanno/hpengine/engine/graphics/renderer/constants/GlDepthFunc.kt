package de.hanno.hpengine.engine.graphics.renderer.constants

import org.lwjgl.opengl.GL11

enum class GlDepthFunc(val glFunc: Int) {
    LESS(GL11.GL_LESS), LEQUAL(GL11.GL_LEQUAL), EQUAL(GL11.GL_EQUAL), GREATER(GL11.GL_GREATER);
}