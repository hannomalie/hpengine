package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import org.lwjgl.opengl.ARBBindlessTexture

interface Texture {
    val width: Int
    val height: Int
    val textureId: Int
    val target: GlTextureTarget
    val handle: Long
    val lastUsedTimeStamp: Long
    val minFilter: Int
    val magFilter: Int
    fun unload() {}
    fun setUsedNow() { }

    companion object {
        fun genHandle(textureManager: TextureManager, textureId: Int): Long {
            return textureManager.gpuContext.calculate {
                val theHandle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
                ARBBindlessTexture.glMakeTextureHandleResidentARB(theHandle)
                theHandle
            }
        }
    }
}