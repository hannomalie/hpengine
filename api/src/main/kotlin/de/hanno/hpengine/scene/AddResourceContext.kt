package de.hanno.hpengine.scene

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class AddResourceContext {
    val channel = Channel<() -> Any>(Channel.Factory.UNLIMITED)

    fun launch(block: () -> Unit) {
        GlobalScope.launch {
            channel.send(block)
        }
    }
}