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
import org.lwjgl.opengl.GL21.GL_RGBA
import org.lwjgl.opengl.GL21.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL45.glCompressedTextureSubImage2D
import org.lwjgl.opengl.GL45.glTextureSubImage2D
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

class OpenGLPixelBufferObject(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val buffer: GpuBuffer = graphicsApi.PersistentMappedBuffer(BufferTarget.PixelUnpack, SizeInBytes(5_000_000))
): PixelBufferObject {
    override var uploading = false
        private set

    private val singleThreadContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    init {
        graphicsApi.unbindPixelBufferObject()
    }
    override fun upload(handle: TextureHandle<Texture2D>, imageData: ImageData) = GlobalScope.launch(singleThreadContext) {
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

                    if (config.debug.simulateSlowTextureStreaming) {
                        println("Uploaded level ${imageData.mipMapLevel}")
                        Thread.sleep((imageData.mipMapLevel * 100).toLong())
                    }

                    onGpu {
                        profiled("textureSubImage") {
                            buffer.bound {
                                if (texture.internalFormat.isCompressed) profiled("glCompressedTextureSubImage2D") {
                                    glCompressedTextureSubImage2D(textureId, imageData.mipMapLevel, 0, 0, imageData.width, imageData.height, texture.internalFormat.glValue, capacity, 0)
                                } else profiled("glTextureSubImage2D") {
                                    glTextureSubImage2D(textureId, imageData.mipMapLevel, 0, 0, imageData.width, imageData.height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                                }
                            }
                        }
                        when (val uploadState = handle.uploadState) {
                            is UploadState.Unloaded, is UploadState.MarkedForUpload -> {
                                handle.uploadState = UploadState.Uploading(imageData.mipMapLevel)
                            }

                            UploadState.Uploaded -> {}
                            is UploadState.Uploading -> {
                                if (uploadState.mipMapLevel < imageData.mipMapLevel) {
                                    handle.uploadState = UploadState.Uploading(imageData.mipMapLevel)
                                }
                            }

                            UploadState.ForceFallback -> {}
                        }
                        CommandSync {
                            if (imageData.mipMapLevel == 0) {
                                handle.uploadState = UploadState.Uploaded
                            }
                        }
                    }
                }
            }
            if (config.debug.simulateSlowTextureStreaming) {
                Thread.sleep(100L)
            }
        } catch (e: CancellationException) {
            handle.uploadState = UploadState.Unloaded
            handle.currentMipMapBias = mipMapBiasForUploadState(UploadState.Unloaded, handle.texture!!.dimension)
        } finally {
            buffer.unbind()
            uploading = false
        }
    }

    fun delete() {
        buffer.delete()
    }
}
