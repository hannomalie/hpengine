package de.hanno.hpengine.graphics.renderer.constants

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL32

enum class GlCap(val glInt: Int) {
    TEXTURE_CUBE_MAP_SEAMLESS(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS),
    DEPTH_TEST(GL11.GL_DEPTH_TEST),
    CULL_FACE(GL11.GL_CULL_FACE),
    BLEND(GL11.GL_BLEND);

    var enabled = GL11.glIsEnabled(glInt)

    fun enable() {
        if (!enabled) {
            GL11.glEnable(glInt)
            enabled = true
        }
    }

    fun disable() {
        if (enabled) {
            GL11.glDisable(glInt)
            enabled = false
        }
    }
}

sealed class GlFlag(var enabled: Boolean) {
    abstract fun enable()
    abstract fun disable()

    object DEPTH_MASK: GlFlag(GL11.glGetBoolean(GL_DEPTH_WRITEMASK)) {
        override fun enable() {
            if(!enabled) {
                GL11.glDepthMask(true)
                enabled = true
            }
        }

        override fun disable() {
            if(enabled) {
                GL11.glDepthMask(false)
                enabled = false
            }
        }
    }
}
