package de.hanno.hpengine.graphics.executors

import de.hanno.hpengine.graphics.GpuExecutor
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.window.Window
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class OpenGlExecutorImpl(
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    override val gpuProfiler: GPUProfiler,
    initBlock: () -> Unit,
) : CoroutineScope, GpuExecutor {
    override var parentContext: Window? = null
    private val openGLThreadName = OpenGLContext.createOpenGLThreadName()
    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = openGLThreadName
        Thread.currentThread().id
    }
    init {
        runBlocking(dispatcher) {initBlock() }
    }
    override val backgroundContext: GpuExecutor? = null

    override var perFrameAction: (() -> Unit)? = null
    override var loopCondition: (() -> Boolean)? = { true }
    override var afterLoop: (() -> Unit)? = {  }
    override val coroutineContext = dispatcher + Job()

    inline val isOpenGLThread: Boolean get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean get() = id == gpuThreadId

    override suspend fun <T> execute(block: () -> T) = if (isOpenGLThread) {
        block()
    } else {
        withContext(coroutineContext) {
            block()
        }
    }

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE) = if (isOpenGLThread) {
        block()
    } else {
        runBlocking(coroutineContext) {
            block()
        }
    }
    override fun launch(block: () -> Unit) {
        if (isOpenGLThread) {
            block()
        } else {
            GlobalScope.launch(coroutineContext) {
                block()
            }
        }
    }
}