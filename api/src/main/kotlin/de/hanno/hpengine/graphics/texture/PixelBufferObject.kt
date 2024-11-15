package de.hanno.hpengine.graphics.texture

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

interface PixelBufferObject {
    fun upload(handle: TextureHandle<Texture2D>, imageData: ImageData): Job
    val uploading: Boolean
}

data class Task(val handle: TextureHandle<Texture2D>, val priority: Int, val action: (PixelBufferObject) -> Job) {
    fun run(pbo: PixelBufferObject): Job = action(pbo)
}
interface PixelBufferObjectPool {
    fun scheduleUpload(handle: TextureHandle<Texture2D>, data: List<ImageData>)
    val buffers: List<PixelBufferObject>
    val currentJobs: ConcurrentHashMap<TextureHandle<*>, Job>
}