package de.hanno.hpengine.util.commandqueue

import kotlinx.coroutines.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.logging.Level
import java.util.logging.Logger

open class CommandQueue @JvmOverloads constructor(executorService: ExecutorService, private val executeDirectly: () -> Boolean = { false }) {
    val executor = executorService
    val dispatcher = executor.asCoroutineDispatcher()

    fun executeCommands(): Boolean {
        runBlocking {
            launch(dispatcher) {
                try {
                    yield()
                } catch (e: Error) {
                    LOGGER.log(Level.SEVERE, "", e)
                }
            }
        }
        return true
    }

    fun addCommand(runnable: Runnable): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        execute(Runnable { runnable.run().apply { result.complete(null) } } , true)
        return result
    }

    fun <RESULT_TYPE> addCommand(command: FutureCallable<RESULT_TYPE>): CompletableFuture<RESULT_TYPE> {
        if (executeDirectly.invoke()) {
            try {
                command.future.complete(command.execute())
            } catch (e: Exception) {
                e.printStackTrace()
                command.future.completeExceptionally(e)
            }
        } else {
            execute(Runnable { command.execute().also { command.future.complete(it) } }, false)
        }

        return command.future
    }

    fun <RESULT_TYPE> calculate(callable: Callable<RESULT_TYPE>): RESULT_TYPE? {
        if(executeDirectly()) {
            return callable.call()
        }
        return runBlocking {
            withContext(dispatcher) {
                callable.call()
            }
        }
    }

    fun execute(runnable: Runnable, andBlock: Boolean) {
        if (executeDirectly.invoke()) {
            runnable.run()
        }
        if(andBlock) {
            runBlocking {
                withContext(dispatcher) {
                    runnable.run()
                }
            }
        } else {
            GlobalScope.launch(dispatcher) {
                runnable.run()
            }
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(CommandQueue::class.java.name)
    }
}
