package de.hanno.hpengine.graphics.texture

interface PixelBufferObject {
    fun upload(handle: TextureHandle<Texture2D>, data: List<ImageData>)
    fun upload(handle: TextureHandle<Texture2D>, level: Int, imageData: ImageData)
}

interface PixelBufferObjectPool {
    fun scheduleUpload(handle: TextureHandle<Texture2D>, data: List<ImageData>)
}