package de.hanno.hpengine.graphics.executors

import de.hanno.hpengine.graphics.GpuExecutor
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.logger
import de.hanno.hpengine.graphics.profiledFoo
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.GLU
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.lifecycle.Termination
import kotlinx.coroutines.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL11
import org.lwjgl.system.APIUtil
import java.lang.reflect.Field
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class FrameBasedResultOpenGLExecutor(
    private val window: Window,
    override val gpuProfiler: GPUProfiler,
    override val backgroundContext: GpuExecutor?,
    private val termination: Termination,
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    initBlock: () -> Unit,
): GpuExecutor {
    override var parentContext: Window? = null

    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = OpenGLContext.createOpenGLThreadName()
        Thread.currentThread().id
    }
    private val queue: BlockingQueue<() -> Unit> = LinkedBlockingQueue()
    override var perFrameAction: (() -> Unit)? = null
    override var loopCondition: (() -> Boolean)? = { !termination.terminationRequested.get() }
    override var afterLoop: (() -> Unit)? = {  }

    init {
        var frameCounter = 0

        runBlocking(dispatcher) { initBlock() }

        GlobalScope.launch(dispatcher) {
            while (loopCondition?.invoke() == true) {
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
            while(!termination.terminationAllowed.get()) {
                Thread.sleep(100)
                logger.info("Waiting for termination to be allowed")
            }
            afterLoop?.invoke()
        }
    }

    inline val isOpenGLThread: Boolean get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean get() = id == gpuThreadId

    override suspend fun <T> execute(block: () -> T): T {
        val result = CompletableFuture<Result<T>>()
        val catchingBlock = runCatching {
            block().apply {
                if(GL11.glGetError() != GL11.GL_NO_ERROR) { throw RuntimeException() }
            }
        }
        queue.put { result.complete(catchingBlock) }

        return result.get().getOrElse {
            throw RuntimeException(it)
        }
    }

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE) = if (isOpenGLThread) {
        block().apply {
            if(GL11.glGetError() != GL11.GL_NO_ERROR) { throw RuntimeException() }
        }
    } else {
        val result = CompletableFuture<Result<RETURN_TYPE>>()
        queue.put {
            val catchingBlock = runCatching {
                block().apply {
                    if(GL11.glGetError() != GL11.GL_NO_ERROR) { throw RuntimeException() }
                }
            }
            result.complete(catchingBlock)
        }
        result.get().getOrElse {
            throw RuntimeException(it)
        }
    }

    override fun launch(block: () -> Unit) = if (isOpenGLThread) {
        block().apply {
            if(GL11.glGetError() != GL11.GL_NO_ERROR) { throw RuntimeException() }
        }
    } else {
        queue.put {
            val runCatching = runCatching {
                block().apply {
                    if(GL11.glGetError() != GL11.GL_NO_ERROR) { throw RuntimeException() }
                }
            }
            runCatching.onFailure {
                it.printStackTrace()
            }
        }
    }
}

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