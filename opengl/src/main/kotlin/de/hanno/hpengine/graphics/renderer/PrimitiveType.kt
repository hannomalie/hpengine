package de.hanno.hpengine.graphics.renderer

import PrimitiveType
import org.lwjgl.opengl.ARBTessellationShader.GL_PATCHES
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES

val PrimitiveType.glValue: Int
    get() = when(this) {
        PrimitiveType.Lines -> GL_LINES
        PrimitiveType.Triangles -> GL_TRIANGLES
        PrimitiveType.Patches -> GL_PATCHES
    }
