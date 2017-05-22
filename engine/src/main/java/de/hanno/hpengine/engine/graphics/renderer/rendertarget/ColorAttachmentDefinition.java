package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import org.lwjgl.opengl.GL11;

public class ColorAttachmentDefinition {
    int internalFormat = GL11.GL_RGB;
    int textureFilter = GL11.GL_LINEAR;

    public ColorAttachmentDefinition() {}

    public ColorAttachmentDefinition setInternalFormat(int internalFormat) {
        this.internalFormat = internalFormat;
        return this;
    }
    public ColorAttachmentDefinition setTextureFilter(int textureFilter) {
        this.textureFilter = textureFilter;
        return this;
    }
}
