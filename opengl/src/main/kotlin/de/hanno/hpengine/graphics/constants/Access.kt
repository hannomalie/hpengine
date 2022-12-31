package de.hanno.hpengine.graphics.constants

import de.hanno.hpengine.graphics.Access
import org.lwjgl.opengl.GL15.*

val Access.glValue: Int
    get() = when(this) {
        Access.ReadOnly -> GL_READ_ONLY
        Access.WriteOnly -> GL_WRITE_ONLY
        Access.ReadWrite -> GL_READ_WRITE
    }