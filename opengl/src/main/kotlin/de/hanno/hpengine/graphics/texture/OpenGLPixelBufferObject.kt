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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor

class OpenGLPixelBufferObject(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    _buffer: GpuBuffer? = null
): PixelBufferObject {
    private val buffer = _buffer ?: graphicsApi.PersistentMappedBuffer(BufferTarget.PixelUnpack, SizeInBytes(5_000_000))
    var uploading = AtomicBoolean(false)

    init {
        graphicsApi.unbindPixelBufferObject()
    }
    override fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {
        if(uploading.getAndSet(true)) throw IllegalStateException("PBO already uploading!")

        when(info) {
            is UploadInfo.AllMipLevelsTexture2DUploadInfo -> {

                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height

                info.data.forEachIndexed { index, data ->
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
                uploading.getAndSet(false)
            }
            is UploadInfo.SingleMipLevelTexture2DUploadInfo -> {
                when(val data = info.data) {
                    null -> {
                        uploading.getAndSet(false)
                    }
                    else -> {
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
                                uploading.getAndSet(false)
                                texture.uploadState = UploadState.Uploaded
                            }
                        }
                    }
                }
            }
            is UploadInfo.AllMipLevelsLazyTexture2DUploadInfo -> {
                info.data.asReversed().forEachIndexed { level, foo ->
                    val level1 = info.data.size - 1 - level
                    uploading.getAndSet(true)
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
                            texture.uploadState = UploadState.Uploading(level1)
                            CommandSync {
                                uploading.getAndSet(false)
                                if (level1 == 0) {
                                    texture.uploadState = UploadState.Uploaded
                                }
                            }
                        }
                    }
                    while(uploading.get()) {}
                }
            }
            is UploadInfo.SingleMipLevelsLazyTexture2DUploadInfo -> {
                info.data.let { textureData ->

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
                            uploading.getAndSet(false)
                            texture.uploadState = UploadState.Uploaded
                        }
                    }
                }
            }
        }
    }

    override fun upload(
        texture: Texture2D,
        level: Int,
        info: UploadInfo.Texture2DUploadInfo,
        lazyTextureData: LazyTextureData
    ): Unit = graphicsApi.run {
        uploading.getAndSet(true)

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
//        (backgroundContext ?: this).onGpu { // TODO: In here happen errors, investigate why
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
                uploading.getAndSet(false)
                if(level == 0) {
                    texture.uploadState = UploadState.Uploaded
                }
            }
        }
        texture.uploadState = UploadState.Uploading(level)
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