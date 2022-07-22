package de.hanno.hpengine.graphics.query

import org.lwjgl.opengl.GL33

enum class Target(val glTarget: Int) {
    TIME_ELAPSED(GL33.GL_TIME_ELAPSED);
}