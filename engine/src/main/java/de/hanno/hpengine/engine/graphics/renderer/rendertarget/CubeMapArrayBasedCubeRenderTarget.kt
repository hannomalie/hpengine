package de.hanno.hpengine.engine.graphics.renderer.rendertarget

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.util.Util
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*


class CubeMapArrayBasedCubeRenderTarget(val engine: Engine, val cubeMapArray: CubeMapArray, useDepthBuffer: Boolean = true): RenderTarget(engine.gpuContext) {
    val cubeMapViews = IntArray(cubeMapArray.cubeMapCount)
    val cubeMapHandles = LongArray(cubeMapArray.cubeMapCount)

    init {
        width = cubeMapArray.width
        height = cubeMapArray.height
        clearR = 0f
        clearG = 0f
        clearB = 0f
        clearA = 0f

        val colorBufferCount = cubeMapArray.cubeMapCount
        renderedTextures = IntArray(colorBufferCount)

        frameBuffer = gpuContext.genFrameBuffer()
        gpuContext.bindFrameBuffer(frameBuffer)

        scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount)

        for (cubeMapIndex in 0 until cubeMapArray.cubeMapCount) {

            val cubeMapView = this.gpuContext.genTextures()
            this.gpuContext.execute {
                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.textureID,
                        cubeMapArray.internalFormat, 0, Util.calculateMipMapCount(cubeMapArray.width),
                        6 * cubeMapIndex, 6)

            }
            val handle = this.gpuContext.calculate { ARBBindlessTexture.glGetTextureHandleARB(cubeMapView) }
            cubeMapViews[cubeMapIndex] = cubeMapView
            cubeMapHandles[cubeMapIndex] = handle
            this.gpuContext.execute { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        }

        gpuContext.execute({
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, cubeMapArray.textureID, 0)
        })
        renderedTextures[0] = cubeMapArray.textureID

        scratchBuffer.put(0, GL30.GL_COLOR_ATTACHMENT0)


        gpuContext.execute {
            if (useDepthBuffer) {
                val depthCubeMap = engine.textureManager.getCubeMap(width, height, GL14.GL_DEPTH_COMPONENT24)
                GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depthCubeMap, 0)
            }
            GL20.glDrawBuffers(scratchBuffer)

            //TODO: Make this more pretty
            val framebufferCheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
            if (framebufferCheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.err.println("CubeRenderTarget fucked up with $framebufferCheck")
                System.exit(0)
            }
        }
    }
}