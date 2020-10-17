package de.hanno.hpengine.engine.scene

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

class AddResourceContext {
    val channel = Channel<() -> Any>(Channel.Factory.UNLIMITED)

    fun launch(block: () -> Unit) {
        GlobalScope.launch {
            channel.send(block)
        }
    }

    fun locked(block: () -> Unit) {
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