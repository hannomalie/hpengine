package de.hanno.hpengine.engine.graphics.renderer.constants

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

enum class BlendMode(val mode: Int) {
    FUNC_ADD(GL14.GL_FUNC_ADD);

    enum class Factor(val glFactor: Int) {
        ZERO(GL11.GL_ZERO), ONE(GL11.GL_ONE), SRC_ALPHA(GL11.GL_SRC_ALPHA), ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}