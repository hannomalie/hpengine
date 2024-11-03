package de.hanno.hpengine.graphics.texture

import java.util.concurrent.PriorityBlockingQueue

interface PixelBufferObject {
    fun upload(handle: TextureHandle<Texture2D>, data: List<ImageData>)
    fun upload(handle: TextureHandle<Texture2D>, level: Int, imageData: ImageData)
    val uploading: Boolean
}

data class Task(val handle: TextureHandle<Texture2D>, val priority: Int, val action: (PixelBufferObject) -> Unit) {
    fun run(pbo: PixelBufferObject) {
        action(pbo)
    }
}
interface PixelBufferObjectPool {
    fun scheduleUpload(handle: TextureHandle<Texture2D>, data: List<ImageData>)
    val buffers: List<PixelBufferObject>
    val queue: PriorityBlockingQueue<Task>
}