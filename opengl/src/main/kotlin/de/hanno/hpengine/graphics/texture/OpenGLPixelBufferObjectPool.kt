package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import org.apache.logging.log4j.LogManager
import java.util.concurrent.*
import java.util.concurrent.CancellationException

private val logger = LogManager.getLogger("OpenGLPixelBufferObjectPool")

class OpenGLPixelBufferObjectPool(
    graphicsApi: GraphicsApi,
    config: Config,
): PixelBufferObjectPool {
    override val buffers = buildList {
        add(
            OpenGLPixelBufferObject(graphicsApi, config, PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, SizeInBytes(10_000_000)))
        )
        repeat(6) {
            add(
                OpenGLPixelBufferObject(graphicsApi, config, PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, SizeInBytes(5_000_000)))
            )
        }
        repeat(10) {
            add(
                OpenGLPixelBufferObject(graphicsApi, config, PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, SizeInBytes(5_000)))
            )
        }
        repeat(10) {
            add(
                OpenGLPixelBufferObject(graphicsApi, config, PersistentMappedBuffer(graphicsApi, BufferTarget.PixelUnpack, SizeInBytes(1_000)))
            )
        }
    }.sortedBy { it.buffer.sizeInBytes.value }
    private val staging = ConcurrentHashMap<Atom, Task>()
    override val currentJobs = ConcurrentHashMap<TextureHandle<*>, Job>()

    private val channels = (0..20).map { Channel<SubAtom>(100) }
    init {
        GlobalScope.launch {
            while(true) {
                select {
                    for (channel in channels.reversed()) {
                        channel.onReceive { atom ->
                            var buffer: PixelBufferObject? = null
                            while(buffer == null) {
                                buffer = buffers.firstOrNull {
                                    !it.uploading && it.buffer.sizeInBytes >= atom.description.gpuSizeOfImageInBytes
                                }
                            }
                            launch {
                                val start = System.nanoTime()
                                try {
                                    val data = atom.imageData.dataProvider()
                                    buffer.upload(atom.handle, atom.imageData.copy(dataProvider = { data })).join()
                                    currentJobs.remove(atom.handle)
                                    atom.isCancelled.send(false)
                                } catch (e: CancellationException) {
                                    currentJobs.remove(atom.handle)
                                    logger.debug("Upload cancelled $atom")
                                    // todo: do i need to reset the state here?
//                                    atom.handle.uploadState = UploadState.Unloaded
//                                    atom.handle.currentMipMapBias = mipMapBiasForUploadState(
//                                        UploadState.Unloaded,
//                                        atom.handle.description.dimension
//                                    )

                                    atom.isCancelled.send(true)
                                }
                                logger.debug("Uploading ${atom.handle} - ${atom.imageData} took ${ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) } ms")
                            }.apply {
                                currentJobs[atom.handle] = this
                            }
                        }
                    }
                }
            }
        }
    }

    data class Atom(val handle: TextureHandle<Texture2D>, val imageData: List<ImageData>)
    data class SubAtom(
        val handle: TextureHandle<Texture2D>,
        val imageData: ImageData,
        val isCancelled: Channel<Boolean> = Channel(1),
        val description: TextureDescription.Texture2DDescription,
    )

    fun update() = runBlocking {
        staging.forEach { (atom, task) ->
            if (currentJobs.contains(task.handle)) {
                currentJobs.remove(task.handle)?.cancelAndJoin()
            } else {
                buffers.firstOrNull { !it.uploading }?.let { buffer ->
                    staging.remove(atom)

                    GlobalScope.launch {
                        var cancelled = false
                        atom.imageData.forEach {
                            if (cancelled) {
                                logger.debug("Not uploading $atom because cancelled")
                            } else {
                                val subAtom = SubAtom(atom.handle, it, description = (atom.handle.description as TextureDescription.Texture2DDescription).copy(dimension = TextureDimension2D(it.width, it.height)))
                                channels[it.mipMapLevel].send(subAtom)
                                cancelled = subAtom.isCancelled.receive()
                            }
                        }
                    }
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

        when(handle.texture) {
            null -> { }
            else -> when {
                data.isEmpty() -> throw IllegalStateException("Cannot upload empty data!")
                else -> {
                    staging[Atom(handle, data.sortedByDescending { it.mipMapLevel })] = Task(handle, 0) { pbo ->
                        GlobalScope.launch {
                            data.forEach {
                                pbo.upload(handle, it).join()
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