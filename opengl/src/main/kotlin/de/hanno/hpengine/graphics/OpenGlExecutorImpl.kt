package de.hanno.hpengine.graphics

import PriorityExecutor
import kotlinx.coroutines.*

class OpenGlExecutorImpl(
//    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val dispatcher: PriorityExecutor = PriorityExecutor()
) : CoroutineScope, GpuExecutor {
    var gpuThreadId: Long = runBlocking(dispatcher.priorityDispatcher) {
        Thread.currentThread().name = OpenGLContext.OPENGL_THREAD_NAME
        Thread.currentThread().id
    }

    override val coroutineContext = dispatcher.priorityDispatcher + Job()

    inline val isOpenGLThread: Boolean get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean get() = id == gpuThreadId

    override suspend fun <T> execute(block: () -> T) = if (isOpenGLThread) {
        block()
    } else {
        dispatcher.launch(0) {
            block()
        }
    }

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE) = if (isOpenGLThread) {
        block()
    } else {
        runBlocking(coroutineContext) {
            coroutineScope {
                block()
            }
        }
    }

    override fun <T> async(priority: Int, block: () -> T) = dispatcher.submitAction(priority, block)
}