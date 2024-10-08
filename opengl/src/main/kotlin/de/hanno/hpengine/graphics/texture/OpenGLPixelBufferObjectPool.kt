package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue

class OpenGLPixelBufferObjectPool(
    graphicsApi: GraphicsApi,
    config: Config,
): PixelBufferObjectPool {
    private val buffers = buildList {
        repeat(6) {
            add(
                OpenGLPixelBufferObject(
                    graphicsApi,
                    config,
                    PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, SizeInBytes(5_000_000))
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

                    while(pbo.uploading) { }
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

    override fun scheduleUpload(handle: TextureHandle<Texture2D>, data: List<ImageData>) {

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

        when(val texture = handle.texture) {
            null -> { }
            else -> when {
                data.isEmpty() -> throw IllegalStateException("Cannot upload empty data!")
                data.size > 1 -> {
                    queue.put(Task(0) { pbo ->
                        data.reversed().forEachIndexed { index, textureData ->
                            val level = data.size - 1 - index
                            val currentlyLoadedLevel = when(val uploadState = handle.uploadState) {
                                is UploadState.Unloaded -> uploadState.mipMapLevelToKeep ?: data.size
                                UploadState.Uploaded -> 0
                                is UploadState.Uploading -> uploadState.mipMapLevel
                                is UploadState.MarkedForUpload -> data.size
                            }
                            if(level < currentlyLoadedLevel) {
                                try {
                                    pbo.upload(handle, level, textureData)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    })
                }
                else -> {
                    queue.put(Task(0) { pbo ->
                        pbo.upload(handle, data)
                    })
                }
            }
        }
    }
}

private object TaskComparator: Comparator<Task> {
    override fun compare(o1: Task, o2: Task) = o2.priority.compareTo(o1.priority)
}