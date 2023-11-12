package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.OpenGLGpuBuffer
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import org.lwjgl.opengl.GL11
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

class OpenGLPixelBufferObjectPool(
    graphicsApi: GraphicsApi,
    config: Config,
): PixelBufferObjectPool {
    private val buffers = buildList {
        repeat(3) {
            add(
                OpenGLPixelBufferObject(
                    graphicsApi,
                    config,
                    PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, 5_000_000)
                )
            )
        }

    }
    private val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
        repeat(buffers.size) { index ->
            val pbo = buffers[index]

            submit {
                var current = queue.take()
                while(current != null) {

                    while(pbo.uploading.get()) { }
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

//        This works as well, use for debugging when you want to guarantee no overlapping use of pbo
//        queue.put(Task(0) { pbo ->
//            OpenGLPixelBufferObject(
//                graphicsApi,
//                config,
//                OpenGLGpuBuffer(graphicsApi, BufferTarget.PixelUnpack, 5_000_000)
//            ).apply {
//                upload(info, texture)
//                delete()
//            }
//        })

//        This works as well, use for debugging when you want to guarantee no overlapping use of pbo when using only one above
//        queue.put(Task(0) { pbo ->
//            pbo.apply {
//                upload(info, texture)
//            }
//        })

        when(info) {
            is UploadInfo.AllMipLevelsLazyTexture2DUploadInfo -> {
                info.data.reversed().forEachIndexed { index, textureData ->
                    val level = info.data.size - 1 - index
                    queue.put(Task(level) { pbo ->
                        pbo.upload(texture, level, info, textureData)
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