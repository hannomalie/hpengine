package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.graphics.constants.PrimitiveType
import org.lwjgl.opengl.ARBTessellationShader.GL_PATCHES
import org.lwjgl.opengl.GL11.*

val PrimitiveType.glValue: Int
    get() = when(this) {
        PrimitiveType.Lines -> GL_LINES
        PrimitiveType.Triangles -> GL_TRIANGLES
        PrimitiveType.Patches -> GL_PATCHES
        PrimitiveType.Points -> GL_POINTS
    }
