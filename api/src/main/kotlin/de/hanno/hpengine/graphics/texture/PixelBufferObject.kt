package de.hanno.hpengine.graphics.texture

interface PixelBufferObject {
    fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D)
}

interface PixelBufferObjectPool {
    fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D)
}