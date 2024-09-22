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
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.Semaphore
import kotlin.math.floor

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
    override fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {

        when(info) {
            is UploadInfo.AllMipLevelsTexture2DUploadInfo -> {

                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height

                semaphore.acquire()
                info.data.forEachIndexed { index, textureData ->

                    val data = textureData.dataProvider()
                    data.rewind()
                    buffer.put(data)

                    val level = index
                    graphicsApi.onGpu {
                        buffer.bound {
                            if (info.dataCompressed) {
                                glCompressedTextureSubImage2D(texture.id, level, 0, 0, currentWidth, currentHeight, info.internalFormat.glValue, data.capacity(), 0)
                            } else {
                                glTextureSubImage2D(texture.id, level, 0, 0, currentWidth, currentHeight, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                            }
                        }
                    }

                    currentWidth = floor(currentWidth * 0.5).toInt()
                    currentHeight = floor(currentHeight * 0.5).toInt()
                }
                texture.uploadState = UploadState.Uploaded
                semaphore.release()
            }
            is UploadInfo.SingleMipLevelTexture2DUploadInfo -> {
                when(val textureData = info.data) {
                    null -> { }
                    else -> {
                        val data = textureData.dataProvider()
                        semaphore.acquire()
                        buffer.buffer.put(data)
                        buffer.buffer.rewind()

                        graphicsApi.onGpu {
                            bindTexture(TextureTarget.TEXTURE_2D, texture.id)
                            buffer.bound {
                                if (info.dataCompressed) {
                                    glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, info.internalFormat.glValue, data.capacity(), 0)
                                } else {
                                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                }
                                generateMipMaps(texture)
                            }
                            CommandSync {
                                texture.uploadState = UploadState.Uploaded
                                semaphore.release()
                            }
                        }
                    }
                }
            }
            is UploadInfo.AllMipLevelsTexture2DUploadInfo -> {
                info.data.asReversed().forEachIndexed { level, foo ->
                    val level1 = info.data.size - 1 - level
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
                                    if (info.dataCompressed) profiled("glCompressedTextureSubImage2D") {
                                        glCompressedTextureSubImage2D(textureId, level1, 0, 0, width, height, info.internalFormat.glValue, capacity, 0)
                                    } else profiled("glTextureSubImage2D") {
                                        glTextureSubImage2D(textureId, level1, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                    }
                                }
                            }
                            when(val uploadState = texture.uploadState) {
                                is UploadState.Unloaded -> {
                                    texture.uploadState = UploadState.Uploading(level)
                                }
                                UploadState.Uploaded -> {}
                                is UploadState.Uploading -> {
                                    if(uploadState.mipMapLevel < level) {
                                        texture.uploadState = UploadState.Uploading(level)
                                    }
                                }
                                is UploadState.MarkedForUpload -> {
                                    texture.uploadState = UploadState.Uploading(level)
                                }
                            }
                            CommandSync {
                                if (level1 == 0) {
                                    texture.uploadState = UploadState.Uploaded
                                }
                                semaphore.release()
                            }
                        }
                    }
                    semaphore.acquire()
                    semaphore.release()
                }
            }
            is UploadInfo.SingleMipLevelTexture2DUploadInfo -> {
                semaphore.acquire()
                info.data?.let { textureData ->
                    val data = textureData.dataProvider()
                    buffer.buffer.put(data)
                    buffer.buffer.rewind()

                    graphicsApi.onGpu {
                        bindTexture(TextureTarget.TEXTURE_2D, texture.id)
                        buffer.bound {
                            if (info.dataCompressed) {
                                glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, info.internalFormat.glValue, data.capacity(), 0)
                            } else {
                                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                            }
                            generateMipMaps(texture)
                        }
                        CommandSync {
                            texture.uploadState = UploadState.Uploaded
                            semaphore.release()
                        }
                    }
                }
            }
        }
        if (config.debug.simulateSlowTextureStreaming) {
            Thread.sleep(100L)
        }
    }

    override fun upload(
        texture: Texture2D,
        level: Int,
        info: UploadInfo.Texture2DUploadInfo,
        lazyTextureData: LazyTextureData
    ): Unit = graphicsApi.run {
        semaphore.acquire()

        val dataProvider = lazyTextureData.dataProvider
        val width = lazyTextureData.width
        val height = lazyTextureData.height

        val data = dataProvider()

        data.rewind()
        buffer.put(data)

        val capacity = data.capacity()
        val textureId = texture.id

        if (config.debug.simulateSlowTextureStreaming) {
            println("Uploaded level $level")
            Thread.sleep((level * 100).toLong())
        }

        graphicsApi.onGpu {
            profiled("textureSubImage") {
                buffer.bound {
                    if (info.dataCompressed) profiled("glCompressedTextureSubImage2D") {
                        glCompressedTextureSubImage2D(textureId, level, 0, 0, width, height, info.internalFormat.glValue, capacity, 0)
                    } else profiled("glTextureSubImage2D") {
                        glTextureSubImage2D(textureId, level, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                    }
                }
            }
            CommandSync {
                if(level == 0) {
                    texture.uploadState = UploadState.Uploaded
                }
                semaphore.release()
            }
        }
        texture.uploadState = UploadState.Uploading(level)
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