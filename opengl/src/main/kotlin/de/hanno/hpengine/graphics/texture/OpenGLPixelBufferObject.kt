package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.sync.GpuCommandSync
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean


context(GraphicsApi, GPUProfiler, Config)
// strange nullable primary constructor param because of compiler issues with context receivers
class OpenGLPixelBufferObject(_buffer: GpuBuffer? = null): PixelBufferObject {
    private val buffer = _buffer ?: GpuBuffer(BufferTarget.PixelUnpack, 5_000_000)
    var uploading = AtomicBoolean(false)

    override fun upload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {
        uploading.getAndSet(true)

        when(info) {
            is UploadInfo.CompleteTexture2DUploadInfo -> {

                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height

                info.data.forEachIndexed { index, data ->
                    data.rewind()
                    buffer.put(data)

                    val level = index
                    onGpu {
                        buffer.bind()
                        if (info.dataCompressed) {
                            glCompressedTextureSubImage2D(texture.id, level, 0, 0, currentWidth, currentHeight, info.internalFormat.glValue, data.capacity(), 0)
                        } else {
                            glTextureSubImage2D(texture.id, level, 0, 0, currentWidth, currentHeight, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                        }
                        buffer.unbind()
                    }

                    currentWidth = (currentWidth * 0.5).toInt()
                    currentHeight = (currentHeight * 0.5).toInt()
                }
                texture.uploadState = UploadState.Uploaded
                uploading.getAndSet(false)
            }
            is UploadInfo.SimpleTexture2DUploadInfo -> {

                info.data?.let { data ->

                    buffer.buffer.put(data)
                    buffer.buffer.rewind()

                    onGpu {
                        bindTexture(TextureTarget.TEXTURE_2D, texture.id)
                        buffer.bind()
                        if (info.dataCompressed) {
                            glCompressedTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, info.internalFormat.glValue, data.capacity(), 0)
                        } else {
                            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                        }
                        generateMipMaps(texture)
                        buffer.unbind()
                        CommandSync {
                            uploading.getAndSet(false)
                            texture.uploadState = UploadState.Uploaded
                        }
                    }
                }
            }
            is UploadInfo.LazyTexture2DUploadInfo -> {
                info.data.asReversed().forEachIndexed { level, foo ->
                    upload(texture, info.data.size - 1 - level, info, foo)
                }
            }
        }
    }

    override fun upload(
        texture: Texture2D,
        level: Int,
        info: UploadInfo.Texture2DUploadInfo,
        lazyTextureData: LazyTextureData
    ) {
        uploading.getAndSet(true)
        val dataProvider = lazyTextureData.dataProvider
        val width = lazyTextureData.width
        val height = lazyTextureData.height

        val data = dataProvider()

        data.rewind()
        buffer.put(data)

        val capacity = data.capacity()
        val textureId = texture.id

        if (debug.simulateSlowTextureStreaming) {
            println("Uploaded level $level")
            Thread.sleep((level * 100).toLong())
        }

//        onGpu {
        (backgroundContext ?: this@GraphicsApi).onGpu {
            profiled("textureSubImage") {
//                buffer.bind()
                glBindBuffer(buffer.target.glValue, buffer.id)
                if (info.dataCompressed) profiled("glCompressedTextureSubImage2D") {
                    glCompressedTextureSubImage2D(textureId, level, 0, 0, width, height, info.internalFormat.glValue, capacity, 0)
                } else profiled("glTextureSubImage2D") {
                    glTextureSubImage2D(textureId, level, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
                glBindBuffer(buffer.target.glValue, 0)
//                buffer.unbind()
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