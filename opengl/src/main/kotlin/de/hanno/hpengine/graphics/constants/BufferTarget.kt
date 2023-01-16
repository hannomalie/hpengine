package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL43

val BufferTarget.glValue: Int
    get() = when(this) {
        BufferTarget.ShaderStorage -> GL43.GL_SHADER_STORAGE_BUFFER
        BufferTarget.ElementArray -> GL15.GL_ELEMENT_ARRAY_BUFFER
        BufferTarget.Array -> GL_ARRAY_BUFFER
        BufferTarget.PixelUnpack -> GL21.GL_PIXEL_UNPACK_BUFFER
    }
