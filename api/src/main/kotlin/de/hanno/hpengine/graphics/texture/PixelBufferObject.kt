package de.hanno.hpengine.graphics.texture

interface PixelBufferObject {
    fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D)
    fun upload(texture: Texture2D, level: Int, info: UploadInfo.Texture2DUploadInfo, lazyTextureData: LazyTextureData)
}

interface PixelBufferObjectPool {
    fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D)
}