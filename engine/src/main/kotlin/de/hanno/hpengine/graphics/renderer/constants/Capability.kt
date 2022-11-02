package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL32

val Capability.glInt get() = when(this) {
    Capability.TEXTURE_CUBE_MAP_SEAMLESS -> GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
    Capability.DEPTH_TEST -> GL11.GL_DEPTH_TEST
    Capability.CULL_FACE -> GL11.GL_CULL_FACE
    Capability.BLEND -> GL11.GL_BLEND
}

fun Capability.disable() {
    GL11.glDisable(glInt)
}

fun Capability.enable() {
    GL11.glEnable(glInt)
}

val Capability.isEnabled: Boolean get() = GL11.glIsEnabled(glInt)

sealed class GlFlag(var enabled: Boolean) {
    abstract fun enable()
    abstract fun disable()

    object DEPTH_MASK : GlFlag(GL11.glGetBoolean(GL_DEPTH_WRITEMASK)) {
        override fun enable() {
            if (!enabled) {
                GL11.glDepthMask(true)
                enabled = true
            }
        }

        override fun disable() {
            if (enabled) {
                GL11.glDepthMask(false)
                enabled = false
            }
        }
    }
}
