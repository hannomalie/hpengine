package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.profiling.GPUProfiler
import kotlinx.coroutines.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class FrameBasedOpenGLExecutor(
    private val gpuProfiler: GPUProfiler,
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
): GpuExecutor {
    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = OpenGLContext.createOpenGLThreadName()
        Thread.currentThread().id
    }
    private val queue: BlockingQueue<() -> Unit> = LinkedBlockingQueue()
    override var perFrameAction: (() -> Unit)? = null

    init {
        var frameCounter = 0
        GlobalScope.launch(dispatcher) {
            while (true) {
                gpuProfiler.run {
                    profiledFoo("Frame") {
                        val maxPerFrame = 1
                        var counter = 0
                        var current: (() -> Unit)? = null
                        do {
                            current?.invoke()
                            current = if(counter < maxPerFrame) queue.poll() else null
                            counter++
                        } while (current != null )

                        perFrameAction?.invoke()
                        dump()
                        if(currentTimings.isNotEmpty()) {
                            println(currentTimings)
                        }
                        if(currentAverages.isNotEmpty()) {
                            println(currentAverages)
                        }
                        frameCounter++
                    }
                }
            }
        }
    }

    inline val isOpenGLThread: Boolean get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean get() = id == gpuThreadId

    override suspend fun <T> execute(block: () -> T) = run {
        val result = CompletableFuture<T>()
        queue.put { result.complete(block()) }
        result.get()
    }

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE) = if (isOpenGLThread) {
        block()
    } else {
        val result = CompletableFuture<RETURN_TYPE>()
        queue.put { result.complete(block()) }
        result.get()
    }
}