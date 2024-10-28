package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.bound
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.profiled
import isCompressed
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.Semaphore

class OpenGLPixelBufferObject(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    _buffer: GpuBuffer? = null
): PixelBufferObject {
    private val buffer = _buffer ?: graphicsApi.PersistentMappedBuffer(BufferTarget.PixelUnpack, SizeInBytes(5_000_000))
    private val semaphore = Semaphore(1)
    val uploading
        get() = semaphore.availablePermits() == 0

    init {
        graphicsApi.unbindPixelBufferObject()
    }
    override fun upload(handle: TextureHandle<Texture2D>, data: List<ImageData>) {
        when(val texture = handle.texture) {
            null -> {}
            else -> {
                when(data.size) {
                    0 -> throw IllegalStateException("Cannot upload empty data!")
                    1 -> {
                        val data = data[0].dataProvider()
                        semaphore.acquire()
                        buffer.buffer.put(data)
                        buffer.buffer.rewind()

                        graphicsApi.onGpu {
                            bindTexture(TextureTarget.TEXTURE_2D, texture.id)
                            buffer.bound {
                                if (texture.internalFormat.isCompressed) {
                                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture.dimension.width, texture.dimension.height, texture.internalFormat.glValue, data.capacity(), 0)
                                } else {
                                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture.dimension.width, texture.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                }
                                generateMipMaps(texture)
                            }
                            CommandSync {
                                handle.uploadState = UploadState.Uploaded
                                semaphore.release()
                            }
                        }
                    }
                    else -> {
                        data.asReversed().forEachIndexed { level, foo ->
                            val level1 = data.size - 1 - level
                            semaphore.acquire()
                            graphicsApi.run {

                                val dataProvider = foo.dataProvider
                                val width = foo.width
                                val height = foo.height

                                val data = dataProvider()

                                data.rewind()
                                buffer.put(data)

                                val capacity = data.capacity()
                                val textureId = texture.id

                                if (config.debug.simulateSlowTextureStreaming) {
                                    println("Uploaded level $level1")
                                    Thread.sleep((level1 * 100).toLong())
                                }

                                graphicsApi.onGpu {
                                    profiled("textureSubImage") {
                                        buffer.bound {
                                            if (texture.internalFormat.isCompressed) profiled("glCompressedTextureSubImage2D") {
                                                glCompressedTextureSubImage2D(textureId, level1, 0, 0, width, height, texture.internalFormat.glValue, capacity, 0)
                                            } else profiled("glTextureSubImage2D") {
                                                glTextureSubImage2D(textureId, level1, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                            }
                                        }
                                    }
                                    when(val uploadState = handle.uploadState) {
                                        is UploadState.Unloaded, is UploadState.MarkedForUpload  -> {
                                            handle.uploadState = UploadState.Uploading(level)
                                        }
                                        UploadState.Uploaded -> {}
                                        is UploadState.Uploading -> {
                                            if(uploadState.mipMapLevel < level) {
                                                handle.uploadState = UploadState.Uploading(level)
                                            }
                                        }

                                        UploadState.ForceFallback -> {}
                                    }
                                    CommandSync {
                                        if (level1 == 0) {
                                            handle.uploadState = UploadState.Uploaded
                                        }
                                        semaphore.release()
                                    }
                                }
                            }
                            semaphore.acquire()
                            semaphore.release()
                        }
                    }
                }
                if (config.debug.simulateSlowTextureStreaming) {
                    Thread.sleep(100L)
                }
            }
        }
    }

    override fun upload(
        handle: TextureHandle<Texture2D>,
        level: Int,
        imageData: ImageData
    ): Unit = graphicsApi.run {
        semaphore.acquire()
        when(val texture = handle.texture) {
            null -> {}
            else -> {
                val dataProvider = imageData.dataProvider
                val width = imageData.width
                val height = imageData.height

                val data = dataProvider()

                data.rewind()
                buffer.put(data)

                val capacity = data.capacity()

                if (config.debug.simulateSlowTextureStreaming) {
                    Thread.sleep((level * 100).toLong())
                }

                graphicsApi.onGpu {
                    profiled("textureSubImage") {
                        buffer.bound {
                            graphicsApi.onGpu {
                                if (texture.internalFormat.isCompressed) profiled("glCompressedTextureSubImage2D") {
                                    glCompressedTextureSubImage2D(texture.id, level, 0, 0, width, height, texture.internalFormat.glValue, capacity, 0)
                                } else profiled("glTextureSubImage2D") {
                                    glTextureSubImage2D(texture.id, level, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                }
                            }
                        }
                    }
                    CommandSync {
                        if(level == 0) {
                            handle.uploadState = UploadState.Uploaded
                        }
                        semaphore.release()
                    }
                }
                handle.uploadState = UploadState.Uploading(level)
            }
        }

        semaphore.acquire()
        semaphore.release()
    }
    fun delete() {
        buffer.delete()
    }
}

class Task(val priority: Int, val action: (PixelBufferObject) -> Unit) {
    fun run(pbo: PixelBufferObject) {
        action(pbo)
    }
}