package de.hanno.hpengine.engine.graphics.renderer.rendertarget

import org.lwjgl.opengl.GL11

data class ColorAttachmentDefinition @JvmOverloads constructor(val name: String, var internalFormat: Int = GL11.GL_RGB, var textureFilter: Int = GL11.GL_LINEAR) {
    fun setInternalFormat(internalFormat: Int): ColorAttachmentDefinition {
        this.internalFormat = internalFormat
        return this
    }

    fun setTextureFilter(textureFilter: Int): ColorAttachmentDefinition {
        this.textureFilter = textureFilter
        return this
    }
}

data class ColorAttachmentDefinitions @JvmOverloads constructor(val names: Array<String>, var internalFormat: Int = GL11.GL_RGB, var textureFilter: Int = GL11.GL_LINEAR)
