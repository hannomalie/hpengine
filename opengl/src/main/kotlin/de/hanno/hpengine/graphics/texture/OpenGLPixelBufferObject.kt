package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.query.GpuTimerQuery
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import de.hanno.hpengine.stopwatch.OpenGLProfilingTask
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

context(GraphicsApi, GPUProfiler)
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
                    onGpu {
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

                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height

                info.data.forEachIndexed { index, dataProvider ->
                    val data = dataProvider()

                    buffer.buffer.rewind()
                    data.rewind()
                    buffer.buffer.put(data)
                    buffer.buffer.rewind()

                    val level = index
                    // TODO: Strange spiked whenever the last mip was updated.
                    onGpu {
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
        }

        texture.uploadState = UploadState.UPLOADED
    }
}

context(GraphicsApi, GPUProfiler)
class OpenGLPixelBufferObjectPool: PixelBufferObjectPool {
    private val buffers = listOf(
        OpenGLPixelBufferObject(),
//        OpenGLPixelBufferObject(),
//        OpenGLPixelBufferObject(),
//        OpenGLPixelBufferObject(),
//        OpenGLPixelBufferObject(),
    )
    private val currentBuffer = AtomicInteger(0)
    private val threadPool = Executors.newFixedThreadPool(buffers.size)

    override fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) {
        CompletableFuture.supplyAsync({
            val bufferCounter = currentBuffer.getAndIncrement()
            val bufferToUse = bufferCounter % buffers.size

            buffers[bufferToUse].let {
                synchronized(it) {
                    it.upload(info, texture)
                }
            }
        }, threadPool)
    }
}