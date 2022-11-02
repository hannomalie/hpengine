package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.renderer.GLU
import org.lwjgl.opengl.GL11
import java.util.logging.Logger
import kotlin.system.exitProcess


val CHECK_ERRORS = false

val LOGGER = Logger.getLogger(GpuContext::class.java.name)

fun exitOnGLError(errorMessage: String) {
    if (!CHECK_ERRORS) {
        return
    }

    val errorValue = GL11.glGetError()

    if (errorValue != GL11.GL_NO_ERROR) {
        val errorString = GLU.gluErrorString(errorValue)
        System.err.println("ERROR - $errorMessage: $errorString")

        RuntimeException("").printStackTrace()
        exitProcess(-1)
    }
}