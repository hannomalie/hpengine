package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.OpenGLGpuBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

context(GraphicsApi, GPUProfiler, Config)
class OpenGLPixelBufferObjectPool: PixelBufferObjectPool {
    private val buffers = listOf(
        OpenGLPixelBufferObject(),
        OpenGLPixelBufferObject(),
        OpenGLPixelBufferObject(),
    )
    private val currentBuffer = AtomicInteger(0)
    private val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
        repeat(buffers.size) {
//        repeat(10) {
            submit {
                var current = queue.take()
                while(current != null) {
                    var pbo: PixelBufferObject? = null

                    while(pbo == null) {
                        pbo = buffers.firstOrNull { !it.uploading.get() }
                    }
                    try {
                        current.run(pbo)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    current = queue.take()
                }
            }
        }
    }

    override fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {
        when(info) {
            is UploadInfo.LazyTexture2DUploadInfo -> {
                info.data.reversed().forEachIndexed { index, textureData ->
                    val level = info.data.size - 1 - index
                    queue.put(Task(level) { pbo ->
//                        pbo.upload(texture, level, info, textureData)
                        OpenGLPixelBufferObject(
                            OpenGLGpuBuffer(BufferTarget.PixelUnpack, textureData.width * textureData.height * 4)
                        ).apply {
                            upload(texture, level, info, textureData)
                            delete()
                        }
                    })
                }
            }
            else -> {
                queue.put(Task(0) { pbo -> pbo.upload(info, texture) })
            }
        }
    }
}

private object TaskComparator: Comparator<Task> {
    override fun compare(o1: Task, o2: Task) = o2.priority.compareTo(o1.priority)
}