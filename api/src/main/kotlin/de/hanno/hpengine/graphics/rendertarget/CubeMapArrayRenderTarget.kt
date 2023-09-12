package de.hanno.hpengine.graphics.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.MagFilter
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.CubeMapArray
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureDimension
import org.joml.Vector4f

class CubeMapArrayRenderTarget(
    private val graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<CubeMapArray>
) : BackBufferRenderTarget<CubeMapArray> by renderTarget {

    val cubeMapViews = ArrayList<CubeMap>()
    val cubeMapFaceViews = ArrayList<Texture2D>()

    init {
        for (cubeMapArrayIndex in textures.indices) {
            val cma = textures[cubeMapArrayIndex]
            graphicsApi.onGpu {
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
        graphicsApi.register(this)
    }

    override fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int) {
        graphicsApi.framebufferTextureLayer(
            attachmentIndex,
            textureId,
            mipmap,
            6 * index
        )
    }

    fun resetAttachments() {
        for (i in textures.indices) {
            graphicsApi.framebufferTextureLayer(i, 0, 0, 0)
        }
    }

    fun getCubeMapArray(i: Int): CubeMapArray = textures[i]

    val arraySize: Int
        get() = textures[0].dimension.depth

    companion object {

        operator fun invoke(
            graphicsApi: GraphicsApi,
            width: Int, height: Int,
            name: String, clear: Vector4f,
            vararg cubeMapArray: CubeMapArray
        ) = CubeMapArrayRenderTarget(
            graphicsApi,
            graphicsApi.RenderTarget(
                graphicsApi.FrameBuffer(createDepthBuffer(graphicsApi, width, height, cubeMapArray.size)),
                width,
                height,
                cubeMapArray.toList(),
                name,
                clear
            )
        )

        fun createDepthBuffer(
            graphicsApi: GraphicsApi,
            width: Int,
            height: Int,
            depth: Int
        ): DepthBuffer<CubeMapArray> {
            val dimension = TextureDimension(width, height, depth)
            val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
            return DepthBuffer(
                graphicsApi.CubeMapArray(
                    dimension,
                    filterConfig,
                    InternalTextureFormat.DEPTH_COMPONENT24,
                    WrapMode.Repeat
                )
            )
        }
    }
}