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
    private val buffersForStaticHandles = buffers
    private val buffersForDynamicHandles = buffers
    override val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val _queue = ConcurrentHashMap<TextureHandle<*>, Task>()
    private val stagingDynamic = ConcurrentHashMap<DynamicHandle<*>, Task>()
    private val stagingStatic = ConcurrentHashMap<StaticHandle<*>, Task>()
    override val currentJobs = ConcurrentHashMap<TextureHandle<*>, Job>()

    fun update() {
        stagingStatic.forEach { (handle, task) ->
            buffersForStaticHandles.firstOrNull { !it.uploading }?.let { buffer ->
                stagingStatic.remove(handle)
                GlobalScope.launch { // TODO: This is not entirely working i think
                    while (buffer.uploading) {
                        delay(100)
                    }
                    val job = task.run(buffer)
                    job.join()
                }
            }
        }
        stagingDynamic.forEach { (handle, task) ->
            if(currentJobs.contains(task.handle)) {
//                TODO: Make cancellation possible
//                currentJobs[value.handle]?.cancel()
//                currentJobs.remove(value.handle)
//                queue.put(value)
            } else {
                stagingDynamic.remove(handle)
//                queue.put(task)
                _queue[handle] = task
                buffersForDynamicHandles.firstOrNull { !it.uploading }?.let { buffer ->
                    GlobalScope.launch {
                        while (buffer.uploading) {
                            delay(100)
                        }
                        val job = task.run(buffer)
                        currentJobs[task.handle] = job
                        job.join()
                    }.invokeOnCompletion {
                        currentJobs.remove(task.handle)
                    }
                }
            }
        }
//        staging.clear()
    }

//    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
//        repeat(buffers.size) { index ->
//            val pbo = buffers[index]
//
//            submit {
////                var current = queue.take()
////                while(current != null) {
//                while(true) {
//
//                    while(pbo.uploading) { }
//                    when(val first = _queue.entries.firstOrNull()) {
//                        null ->  {}
//                        else -> {
//                            val current = _queue.remove(first.key)!!
//                            try {
//                                val job = current.run(pbo)
//                                currentJobs[current.handle] = job
//                                runBlocking {
//                                    job.join()
//                                    currentJobs.remove(current.handle)
//                                }
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                            }
//                        }
//                    }
////                    current = queue.take()
//
//                }
//            }
//        }
//    }

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

        when(handle.texture) {
            null -> { }
            else -> when {
                data.isEmpty() -> throw IllegalStateException("Cannot upload empty data!")
                else -> {
                    when(handle) {
                        is DynamicHandle -> {
                            stagingDynamic[handle] = Task(handle, 0) { pbo ->
                                GlobalScope.launch {
                                    data.sortedByDescending { it.mipMapLevel }.map {
                                        pbo.upload(handle, it).join()
                                    }
                                }
                            }
                        }
                        is StaticHandle -> {
                            stagingStatic[handle] = Task(handle, 0) { pbo ->
                                GlobalScope.launch {
                                    data.sortedByDescending { it.mipMapLevel }.map {
                                        pbo.upload(handle, it).join()
                                    }
                                }
                            }
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