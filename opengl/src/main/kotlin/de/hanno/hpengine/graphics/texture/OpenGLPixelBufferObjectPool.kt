package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue

class OpenGLPixelBufferObjectPool(
    graphicsApi: GraphicsApi,
    config: Config,
): PixelBufferObjectPool {
    override val buffers = buildList {
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
    override val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val staging = ConcurrentHashMap<TextureHandle<*>, Task>()
    private val currentJobs = ConcurrentHashMap<TextureHandle<*>, Job>()

    fun update() = runBlocking {
        staging.forEach { (key, value) ->
            if(currentJobs.contains(value.handle)) {
//                TODO: Make cancellation possible
//                currentJobs[value.handle]?.cancel()
//                currentJobs.remove(value.handle)
//                queue.put(value)
            } else {
                staging.remove(key)
                queue.put(value)
            }
        }
//        staging.clear()
    }

    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
        repeat(buffers.size) { index ->
            val pbo = buffers[index]

            submit {
                var current = queue.take()
                while(current != null) {

                    while(pbo.uploading) { }
                    try {
                        val job = current.run(pbo)
                        currentJobs[current.handle] = job
                        runBlocking {
                            job.join()
                            currentJobs.remove(current.handle)
                        }
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
                    if(!texture.textureFilterConfig.minFilter.isMipMapped) {
                        throw IllegalStateException("Can't upload multiple data to non mip mapped texture!")
                    }
                    staging[handle] = Task(handle, 0) { pbo ->
                        GlobalScope.launch {
                            data.reversed().map {
                                pbo.upload(handle, it).join()
                            }
                        }
                    }
                }
                else -> {
                    staging[handle] = Task(handle, 0) { pbo ->
                        GlobalScope.launch {
                            pbo.upload(handle, data.first()).join()
                        }
                    }
                }
            }
        }
    }
}

private object TaskComparator: Comparator<Task> {
    override fun compare(o1: Task, o2: Task) = o2.priority.compareTo(o1.priority)
}