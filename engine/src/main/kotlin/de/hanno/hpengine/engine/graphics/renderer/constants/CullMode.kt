package de.hanno.hpengine.engine.graphics.renderer.constants

import org.lwjgl.opengl.GL11

enum class CullMode(val glMode: Int) {
    FRONT(GL11.GL_FRONT), BACK(GL11.GL_BACK);
}