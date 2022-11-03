package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.model.texture.CubeMap
import de.hanno.hpengine.model.texture.CubeMapArray
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.model.texture.createView
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import java.util.ArrayList

class CubeMapArrayRenderTarget(
    gpuContext: GpuContext,
    renderTarget: RenderTarget<CubeMapArray>
) : RenderTarget<CubeMapArray> by renderTarget {

    val cubeMapViews = ArrayList<CubeMap>()
    val cubeMapFaceViews = ArrayList<Texture2D>()
    fun setCubeMapFace(attachmentIndex: Int, cubeMapIndex: Int, faceIndex: Int) {
        setCubeMapFace(attachmentIndex, attachmentIndex, cubeMapIndex, faceIndex)
    }

    override fun setCubeMapFace(cubeMapArrayListIndex: Int, attachmentIndex: Int, cubeMapIndex: Int, faceIndex: Int) {
        GL30.glFramebufferTextureLayer(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0 + attachmentIndex,
            textures[cubeMapArrayListIndex].id,
            0,
            6 * cubeMapIndex + faceIndex
        )
    }

    fun resetAttachments() {
        for (i in textures.indices) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, 0, 0, 0)
        }
    }

    fun getCubeMapArray(i: Int): CubeMapArray {
        return textures[i]
    }

    val arraySize: Int
        get() = textures[0].dimension.depth

    init {
        for (cubeMapArrayIndex in textures.indices) {
            val cma = textures[cubeMapArrayIndex]
            gpuContext.invoke {
                gpuContext.bindTexture(cma)
                for (cubeMapIndex in 0 until cma.dimension.depth) {
                    val cubeMapView = cma.createView(gpuContext, cubeMapIndex)
                    cubeMapViews.add(cubeMapView)
                    for (faceIndex in 0..5) {
                        cubeMapFaceViews.add(cma.createView(gpuContext, cubeMapIndex, faceIndex))
                    }
                }
            }
        }
        gpuContext.register(this)
    }

    companion object {

        operator fun invoke(
            gpuContext: GpuContext,
            width: Int, height: Int,
            name: String, clear: Vector4f,
            vararg cubeMapArray: CubeMapArray
        ): CubeMapArrayRenderTarget {
            return CubeMapArrayRenderTarget(
                gpuContext, RenderTarget(
                    gpuContext,
                    FrameBuffer.invoke(gpuContext, createDepthBuffer(gpuContext, width, height, cubeMapArray.size)),
                    width,
                    height,
                    cubeMapArray.toList(),
                    name,
                    clear
                )
            )
        }

        fun createDepthBuffer(
            gpuContext: GpuContext,
            width: Int,
            height: Int,
            depth: Int
        ): DepthBuffer<CubeMapArray> {
            val dimension = TextureDimension(width, height, depth)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            return DepthBuffer(
                CubeMapArray(
                    gpuContext,
                    dimension,
                    filterConfig,
                    GL14.GL_DEPTH_COMPONENT24,
                    GL11.GL_REPEAT
                )
            )
        }
    }
}