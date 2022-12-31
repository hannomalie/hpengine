package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.texture.Texture

interface BackBufferRenderTarget<T : Texture> : RenderTarget {
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

