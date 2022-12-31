package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.WrapMode
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.CubeMapArray
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureDimension
import org.joml.Vector4f

context(GpuContext)
class CubeMapArrayRenderTarget(
    renderTarget: BackBufferRenderTarget<CubeMapArray>
) : BackBufferRenderTarget<CubeMapArray> by renderTarget {

    val cubeMapViews = ArrayList<CubeMap>()
    val cubeMapFaceViews = ArrayList<Texture2D>()

    override fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        framebufferTextureLayer(
            attachmentIndex,
            textureId,
            mipmap,
            6 * index
        )
    }

    fun resetAttachments() {
        for (i in textures.indices) {
            framebufferTextureLayer(i, 0, 0, 0)
        }
    }

    fun getCubeMapArray(i: Int): CubeMapArray = textures[i]

    val arraySize: Int
        get() = textures[0].dimension.depth

    init {
        for (cubeMapArrayIndex in textures.indices) {
            val cma = textures[cubeMapArrayIndex]
            onGpu {
                bindTexture(cma)
                for (cubeMapIndex in 0 until cma.dimension.depth) {
                    val cubeMapView = createView(cma, cubeMapIndex)
                    cubeMapViews.add(cubeMapView)
                    for (faceIndex in 0..5) {
                        cubeMapFaceViews.add(createView(cma, cubeMapIndex, faceIndex))
                    }
                }
            }
        }
        register(this)
    }

    companion object {

        context(GpuContext)
        operator fun invoke(
            width: Int, height: Int,
            name: String, clear: Vector4f,
            vararg cubeMapArray: CubeMapArray
        ) = CubeMapArrayRenderTarget(
            RenderTarget(
                FrameBuffer(createDepthBuffer(width, height, cubeMapArray.size)),
                width,
                height,
                cubeMapArray.toList(),
                name,
                clear
            )
        )

        context(GpuContext)
        fun createDepthBuffer(
            width: Int,
            height: Int,
            depth: Int
        ): DepthBuffer<CubeMapArray> {
            val dimension = TextureDimension(width, height, depth)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            return DepthBuffer(
                CubeMapArray(
                    dimension,
                    filterConfig,
                    InternalTextureFormat.DEPTH_COMPONENT24,
                    WrapMode.Repeat
                )
            )
        }
    }
}