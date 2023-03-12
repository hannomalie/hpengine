package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger


context(GraphicsApi, GPUProfiler, Config)
class OpenGLPixelBufferObject: PixelBufferObject {
    private val buffer = PersistentMappedBuffer(50_000_000, BufferTarget.PixelUnpack)

    override fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) = synchronized(buffer) {
        when(info) {
            is UploadInfo.CompleteTexture2DUploadInfo -> {

                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height

                info.data.forEachIndexed { index, data ->
                    buffer.buffer.rewind()
                    data.rewind()
                    buffer.buffer.put(data)
                    buffer.buffer.rewind()

                    val level = index
                    fencedOnGpu {
                        buffer.bind()
                        if (info.dataCompressed) {
                            glCompressedTextureSubImage2D(
                                texture.id,
                                level,
                                0,
                                0,
                                currentWidth,
                                currentHeight,
                                info.internalFormat.glValue,
                                data.capacity(),
                                0
                            )
                        } else {
                            glTextureSubImage2D(
                                texture.id,
                                level,
                                0,
                                0,
                                currentWidth,
                                currentHeight,
                                GL_RGBA,
                                GL_UNSIGNED_BYTE,
                                0
                            )
                        }
                        buffer.unbind()
                    }

                    currentWidth = (currentWidth * 0.5).toInt()
                    currentHeight = (currentHeight * 0.5).toInt()
                }
            }
            is UploadInfo.SimpleTexture2DUploadInfo -> {

                info.data?.let { data ->

                    buffer.buffer.put(data)
                    buffer.buffer.rewind()

                    onGpu {
                        bindTexture(TextureTarget.TEXTURE_2D, texture.id)
                        buffer.bind()
                        if (info.dataCompressed) {
                            glCompressedTexSubImage2D(
                                GL_TEXTURE_2D,
                                0,
                                0,
                                0,
                                info.dimension.width,
                                info.dimension.height,
                                info.internalFormat.glValue,
                                data.capacity(),
                                0
                            )
                        } else {
                            glTexSubImage2D(
                                GL_TEXTURE_2D,
                                0,
                                0,
                                0,
                                info.dimension.width,
                                info.dimension.height,
                                GL_RGBA,
                                GL_UNSIGNED_BYTE,
                                0
                            )
                        }
                        generateMipMaps(texture)
                        buffer.unbind()
                        finish()
                    }
                }
            }
            is UploadInfo.LazyTexture2DUploadInfo -> {
                info.data.asReversed().forEachIndexed { level, foo ->
                    upload(texture, info.data.size - 1 - level, info, foo)
                }
            }
        }

        texture.uploadState = UploadState.Uploaded
    }

    override fun upload(
        texture: Texture2D,
        level: Int,
        info: UploadInfo.Texture2DUploadInfo,
        foo: Foo
    ) {
        val dataProvider = foo.dataProvider
        val width = foo.width
        val height = foo.height

        val data = dataProvider()

        buffer.buffer.rewind()
        data.rewind()
        buffer.buffer.put(data)
        buffer.buffer.rewind()

        fencedOnGpu {
            buffer.bind()
            if (info.dataCompressed) {
                glCompressedTextureSubImage2D(
                    texture.id,
                    level,
                    0,
                    0,
                    width,
                    height,
                    info.internalFormat.glValue,
                    data.capacity(),
                    0
                )
            } else {
                glTextureSubImage2D(
                    texture.id,
                    level,
                    0,
                    0,
                    width,
                    height,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    0
                )
            }
            buffer.unbind()
        }
        texture.uploadState = UploadState.Uploading(level)

        if (debug.simulateSlowTextureStreaming) {
            println("Uploaded level $level")
            Thread.sleep((level * 100).toLong())
        }
    }
}

context(GraphicsApi, GPUProfiler, Config)
class OpenGLPixelBufferObjectPool: PixelBufferObjectPool {
    private val buffers = listOf(
        OpenGLPixelBufferObject(),
        OpenGLPixelBufferObject(),
    )
    private val currentBuffer = AtomicInteger(0)
    private val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
        repeat(buffers.size) {
            submit {
                var current = queue.take()
                while(current != null) {
                    current.run()
                    current = queue.take()
                }
            }
        }
    }

    override fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {
        when(info) {
            is UploadInfo.LazyTexture2DUploadInfo -> {
                val uploadLevel = { level: Int, foo: Foo ->
                    val bufferCounter = currentBuffer.getAndIncrement()
                    val bufferToUse = bufferCounter % buffers.size

                    buffers[bufferToUse].let {
                        synchronized(it) {
                            it.upload(texture, level, info, foo)
                        }
                    }
                }
                info.data.reversed().forEachIndexed { index, foo ->
                    val level = info.data.size - 1 - index
                    queue.put(Task(level) {
                        uploadLevel(level, foo)
                    })
                }
            }
            else -> {
                val uploadAllLevels = {
                    val bufferCounter = currentBuffer.getAndIncrement()
                    val bufferToUse = bufferCounter % buffers.size

                    buffers[bufferToUse].let {
                        synchronized(it) {
                            it.upload(info, texture)
                        }
                    }
                }

                queue.put(Task(0, uploadAllLevels))
            }
        }
    }
}

class Task(val priority: Int, val action: () -> Unit): Runnable {
    override fun run() {
        action()
    }

}
private object TaskComparator: Comparator<Task> {
    override fun compare(o1: Task, o2: Task) = o2.priority.compareTo(o1.priority)
}