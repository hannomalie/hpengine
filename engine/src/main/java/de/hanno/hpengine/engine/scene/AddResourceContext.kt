package de.hanno.hpengine.engine.scene

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class AddResourceContext {
    private var threadId: Long = -1L
    val singleThreadDispatcher = Executors.newFixedThreadPool(1) { Thread(it).apply {
        name = threadName
        threadId = id
    } }.asCoroutineDispatcher()

    val channel = Channel<() -> Any>(Channel.Factory.UNLIMITED)

    fun launch(block: () -> Unit) {
        GlobalScope.launch {
            channel.send(block)
        }
    }

    fun locked(block: () -> Unit) {
        if(Thread.currentThread().id == threadId) {
            block()
        } else {
            runBlocking {
                channel.send(block)
                CompletableFuture<Unit>().apply {
                    channel.send {
                        this.complete(Unit)
                    }
                }.await()
            }
        }
    }
    companion object {
        val threadName = "AddResourceContextThread"
    }
}