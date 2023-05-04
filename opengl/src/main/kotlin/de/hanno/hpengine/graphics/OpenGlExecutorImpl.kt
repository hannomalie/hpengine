package de.hanno.hpengine.graphics

import kotlinx.coroutines.*
import java.util.concurrent.Executors

class OpenGlExecutorImpl(
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) : CoroutineScope, GpuExecutor {
    private val openGLThreadName = OpenGLContext.createOpenGLThreadName()
    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = openGLThreadName
        Thread.currentThread().id
    }

    override var perFrameAction: (() -> Unit)? = null
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
}