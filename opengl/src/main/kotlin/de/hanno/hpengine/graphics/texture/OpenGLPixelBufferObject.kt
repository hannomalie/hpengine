package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.bound
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.constants.glValue
import de.hanno.hpengine.graphics.profiled
import isCompressed
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL21.GL_RGBA
import org.lwjgl.opengl.GL21.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = LogManager.getLogger("OpenGLPixelBufferObject")

class OpenGLPixelBufferObject(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    val buffer: GpuBuffer = graphicsApi.PersistentMappedBuffer(BufferTarget.PixelUnpack, SizeInBytes(5_000_000))
): PixelBufferObject {
    private val logger = LogManager.getLogger(PixelBufferObject::class.java)

    override var uploading = false
        private set

    private val singleThreadContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    init {
        graphicsApi.unbindPixelBufferObject()
    }
    override fun upload(handle: TextureHandle<Texture2D>, imageData: ImageData) = GlobalScope.launch(singleThreadContext) {
        logger.debug("PBO upload started")
        val start = System.nanoTime()
        try {
            uploading = true

            when(val texture = handle.texture) {
                null -> {  }
                else -> graphicsApi.run {
                    val data = imageData.dataProvider()

                    data.rewind()
                    buffer.put(data)

                    val capacity = data.capacity()
                    val textureId = texture.id

                    profiled("textureSubImage") {
                        onGpu {
                            logger.debug("textureSubImage started")
                            handle.uploadState = UploadState.Uploading(imageData.mipMapLevel)
                            buffer.bound {
                                if (texture.internalFormat.isCompressed) profiled("glCompressedTextureSubImage2D") {
                                    glCompressedTextureSubImage2D(textureId, imageData.mipMapLevel, 0, 0, imageData.width, imageData.height, texture.internalFormat.glValue, capacity, 0)
                                } else profiled("glTextureSubImage2D") {
                                    glTextureSubImage2D(textureId, imageData.mipMapLevel, 0, 0, imageData.width, imageData.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                }
                            }
                            logger.debug("textureSubImage finished")
                        }
                    }
                    if (config.debug.simulateSlowTextureStreaming) {
                        println("Uploaded level ${imageData.mipMapLevel}")
                        Thread.sleep((imageData.mipMapLevel * 100).toLong())
                    }
                }
            }
            if (config.debug.simulateSlowTextureStreaming) {
                Thread.sleep(100L)
            }
            if (imageData.mipMapLevel == 0) {
                handle.uploadState = UploadState.Uploaded
            }
            logger.debug("upload finished: {} {}", handle, imageData)
        } finally {
            buffer.unbind()
            uploading = false
            logger.debug("PBO upload took ${ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) } ms")
        }
    }

    fun delete() {
        buffer.delete()
    }
}
