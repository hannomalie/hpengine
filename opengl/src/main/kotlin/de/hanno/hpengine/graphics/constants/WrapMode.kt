package de.hanno.hpengine.graphics.constants

import de.hanno.hpengine.graphics.constants.WrapMode.ClampToEdge
import de.hanno.hpengine.graphics.constants.WrapMode.Repeat
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE

val WrapMode.glValue: Int
    get() = when(this) {
        Repeat -> GL_REPEAT
        ClampToEdge -> GL_CLAMP_TO_EDGE
    }