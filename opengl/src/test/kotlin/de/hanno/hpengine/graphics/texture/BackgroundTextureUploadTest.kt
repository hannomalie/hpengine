package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.createOpenGLContext
import io.kotest.matchers.ints.shouldNotBeExactly
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11

class BackgroundTextureUploadTest {
    @Test
    fun foo() {
        val (window, mainContext) = createOpenGLContext()
        val backgroundContext = window.gpuExecutor.backgroundContext!!

        val textureFromMainContext = mainContext.onGpu { GL11.glGenTextures() }
        val textureFromUploadContext = backgroundContext.invoke { GL11.glGenTextures() }

        textureFromMainContext shouldNotBeExactly textureFromUploadContext
    }
}