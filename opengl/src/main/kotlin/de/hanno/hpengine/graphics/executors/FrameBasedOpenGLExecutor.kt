package de.hanno.hpengine.graphics.executors

import de.hanno.hpengine.graphics.GpuExecutor
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.profiledFoo
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.GLU
import de.hanno.hpengine.graphics.window.Window
import kotlinx.coroutines.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.APIUtil
import java.lang.reflect.Field
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class FrameBasedOpenGLExecutor(
    override val gpuProfiler: GPUProfiler,
    override val backgroundContext: GpuExecutor?,
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
): GpuExecutor {
    override var parentContext: Window? = null
    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = OpenGLContext.createOpenGLThreadName()
        Thread.currentThread().id
    }
    private val queue: BlockingQueue<() -> Unit> = LinkedBlockingQueue()
    override var perFrameAction: (() -> Unit)? = null
    override var loopCondition: (() -> Boolean)? = { true }
    override var afterLoop: (() -> Unit)? = {  }

    init {
        var frameCounter = 0
        GlobalScope.launch(dispatcher) {
            while (loopCondition?.invoke() != false) {
                gpuProfiler.run {
                    val frameTask = profiledFoo("Frame") {
                        val maxPerFrame = 1
                        var counter = 0
                        var current: (() -> Unit)? = null
                        do {
                            current?.invoke()

                            current = if(counter < maxPerFrame) queue.poll() else null
                            counter++
                        } while (current != null )

                        perFrameAction?.invoke()
                    }

                    dump(frameTask)
                    if(currentTimings.isNotEmpty()) {
                        println(currentTimings)
                    }
                    if(currentAverages.isNotEmpty()) {
                        println(currentAverages)
                    }
                    frameCounter++
                }
            }
            afterLoop?.invoke()
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

    override fun launch(block: () -> Unit) = if (isOpenGLThread) {
        block()
    } else {
        queue.put { block() }
    }
}

class OpenGlException(msg: String): RuntimeException(msg)

private val exceptionOnErrorCallback = object : GLFWErrorCallback() {
    private val ERROR_CODES = APIUtil.apiClassTokens(
        { _: Field?, value: Int -> value in 0x10001..0x1ffff }, null,
        org.lwjgl.glfw.GLFW::class.java
    )

    override fun invoke(error: Int, description: Long) {
//        val msg = getDescription(description)
        "[LWJGL] ${ERROR_CODES[error]} error\n" +
                "Error: ${GLU.gluErrorString(error)}" +
//                "\tDescription : $msg"

        throw OpenGlException(GLU.gluErrorString(error)).apply {
            printStackTrace()
        }
    }
}