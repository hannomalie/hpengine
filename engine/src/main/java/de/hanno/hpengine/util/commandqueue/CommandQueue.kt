package de.hanno.hpengine.util.commandqueue

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

open class CommandQueue @JvmOverloads constructor(executorService: ExecutorService = Executors.newSingleThreadExecutor(), private val executeDirectly: () -> Boolean = { false }) {
    val executor = executorService
    val dispatcher = executor.asCoroutineDispatcher()
    private val channel = Channel<Callable<*>>(Channel.UNLIMITED)

    fun executeCommands(): Boolean {
        runBlocking {
            launch(dispatcher) {
                try {
                    var callable: Callable<*>? = channel.poll()
                    while(callable != null) {
                        callable.call()
                        callable = channel.poll()
                    }
                } catch (e: Error) {
                    LOGGER.log(Level.SEVERE, "", e)
                }
            }
        }
        return true
    }

    fun addCommand(runnable: Runnable): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        if(executeDirectly()) {
            return result.apply { runnable.run(); complete(null) }
        }

        GlobalScope.launch(dispatcher) {
            channel.send(Callable {
                runnable.run()
                result.complete(null)
            })
        }
        return result
    }

    fun <RESULT_TYPE> addCommand(command: FutureCallable<RESULT_TYPE>): CompletableFuture<RESULT_TYPE> {
        if (executeDirectly()) {
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

    fun <RESULT_TYPE> calculate(callable: Callable<RESULT_TYPE>): RESULT_TYPE {
        if(executeDirectly()) {
            return callable.call()
        }
        return runBlocking {
            val future = CompletableFuture<RESULT_TYPE>()
            channel.send(Callable {
                callable.call().apply {
                    future.complete(this)
                }
            })
            future.get()
        }
    }

    fun execute(runnable: Runnable, andBlock: Boolean) {
        if (executeDirectly()) {
            runnable.run()
        }

        val future = CompletableFuture<Void>()
        GlobalScope.launch(dispatcher) {
            channel.send(Callable {
                runnable.run()
                future.complete(null)
            })
        }
        if(andBlock) {
            future.join()
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(CommandQueue::class.java.name)
    }
}