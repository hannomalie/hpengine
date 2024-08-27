package de.hanno.hpengine.graphics.constants

import org.lwjgl.opengl.GL42.*

val Barrier.glValue: Int
    get() = when(this) {
        Barrier.All -> GL_ALL_BARRIER_BITS
        Barrier.ShaderImageAccess -> GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
        Barrier.BufferUpdate -> GL_BUFFER_UPDATE_BARRIER_BIT
    }