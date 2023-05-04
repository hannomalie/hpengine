package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.graphics.buffer.OpenGLGpuBuffer
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

context(GraphicsApi, GPUProfiler, Config)
class OpenGLPixelBufferObjectPool: PixelBufferObjectPool {
    private val buffers = listOf(
        OpenGLPixelBufferObject(),
        OpenGLPixelBufferObject(),
        OpenGLPixelBufferObject(),
    )
    private val currentBuffer = AtomicInteger(0)
    private val queue = PriorityBlockingQueue(10000, TaskComparator)
    private val threadPool = Executors.newFixedThreadPool(buffers.size).apply {
//        repeat(buffers.size) {
        repeat(10) {
            submit {
                var current = queue.take()
                while(current != null) {
                    current.run()
                    current = queue.take()
                }
            }
        }
    }

    override fun scheduleUpload(info: UploadInfo.Texture2DUploadInfo, texture: Texture2D) = when(info) {
        is UploadInfo.LazyTexture2DUploadInfo -> {
            info.data.reversed().forEachIndexed { index, textureData ->
                val level = info.data.size - 1 - index
                queue.put(Task(level) {
                    val bufferCounter = currentBuffer.getAndIncrement()
                    val bufferToUse = bufferCounter % buffers.size
                    buffers[bufferToUse].upload(texture, level, info, textureData)
//                    OpenGLPixelBufferObject(
//                        OpenGLGpuBuffer(BufferTarget.PixelUnpack, textureData.width * textureData.height * 4)
//                    ).apply {
//                        upload(texture, level, info, textureData)
//                        delete()
//                    }
                })
            }
        }
        else -> {
            val uploadAllLevels: () -> Unit = {
                val bufferCounter = currentBuffer.getAndIncrement()
                val bufferToUse = bufferCounter % buffers.size

                buffers[bufferToUse].upload(info, texture)
            }

            queue.put(Task(0, uploadAllLevels))
        }
    }
}

private object TaskComparator: Comparator<Task> {
    override fun compare(o1: Task, o2: Task) = o2.priority.compareTo(o1.priority)
}