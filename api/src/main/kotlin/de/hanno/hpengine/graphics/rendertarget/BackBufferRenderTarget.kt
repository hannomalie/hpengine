package de.hanno.hpengine.graphics.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.CubeMapArray
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.Texture2D

sealed interface BackBufferRenderTarget<T : Texture> : RenderTarget {
    val textures: List<T>
    var renderedTextures: IntArray
    var renderedTextureHandles: LongArray
    var drawBuffers: IntArray
    var mipMapCount: Int
    val renderedTexture: Int
    val factorsForDebugRendering: MutableList<Float>
    val frameBuffer: FrameBuffer

    fun setTargetTexture(textureID: Int, index: Int, mipMapLevel: Int = 0)

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap de.hanno.hpengine.texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    fun setCubeMapFace(attachmentIndex: Int, textureId: Int, index: Int, mipmap: Int)


    fun using(clear: Boolean, block: () -> Unit) = try {
        use(clear)
        block()
    } finally {
        unUse()
    }

    fun use(clear: Boolean)

    fun unUse()
    fun getRenderedTexture(index: Int): Int
    fun setRenderedTexture(renderedTexture: Int, index: Int)
    fun setTargetTextureArrayIndex(textureId: Int, layer: Int)

    companion object

}

class CubeMapRenderTarget(
    graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<CubeMap>
): BackBufferRenderTarget<CubeMap> by renderTarget {
    init {
        graphicsApi.register(this)
    }
}
class RenderTarget2D(
    graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<Texture2D>
): BackBufferRenderTarget<Texture2D> by renderTarget {
    init {
        graphicsApi.register(this)
    }
}
class CubeMapArrayRenderTarget(
    private val graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<CubeMapArray>
): BackBufferRenderTarget<CubeMapArray> by renderTarget {
    init {
        graphicsApi.register(this)
    }
    val cubeMapViews = ArrayList<CubeMap>()
    val cubeMapFaceViews = ArrayList<Texture2D>()
    val cubeMapDepthFaceViews = ArrayList<Texture2D>()

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
                        frameBuffer.depthBuffer?.let {
                            cubeMapDepthFaceViews.add(createView(it.texture as CubeMapArray, cubeMapIndex, faceIndex))
                        }
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

    val arraySize: Int get() = textures[0].dimension.depth

}
