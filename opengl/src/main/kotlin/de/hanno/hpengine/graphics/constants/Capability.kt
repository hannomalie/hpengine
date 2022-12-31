package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK

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
