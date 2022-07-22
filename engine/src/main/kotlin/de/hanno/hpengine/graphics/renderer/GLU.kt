package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.ARBImaging
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

object GLU {
    fun gluErrorString(error_code: Int): String = when (error_code) {
        100900 -> "Invalid enum (glu)"
        100901 -> "Invalid value (glu)"
        100902 -> "Out of memory (glu)"
        else -> Util.translateGLErrorString(error_code)
    }

    fun translateGLErrorString(error_code: Int): String? = when (error_code) {
        GL11.GL_NO_ERROR -> "No error"
        GL11.GL_INVALID_ENUM -> "Invalid enum"
        GL11.GL_INVALID_VALUE -> "Invalid value"
        GL11.GL_INVALID_OPERATION -> "Invalid operation"
        GL11.GL_STACK_OVERFLOW -> "Stack overflow"
        GL11.GL_STACK_UNDERFLOW -> "Stack underflow"
        GL11.GL_OUT_OF_MEMORY -> "Out of memory"
        ARBImaging.GL_TABLE_TOO_LARGE -> "Table too large"
        GL30.GL_INVALID_FRAMEBUFFER_OPERATION -> "Invalid framebuffer operation"
        else -> null
    }
}