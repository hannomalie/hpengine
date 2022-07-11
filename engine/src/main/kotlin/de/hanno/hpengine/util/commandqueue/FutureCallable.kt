package de.hanno.hpengine.util.commandqueue

import java.lang.Exception
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

abstract class FutureCallable<RETURN_TYPE> : CompletableFuture<Callable<RETURN_TYPE>?>() {
    val future: CompletableFuture<RETURN_TYPE>
        get() = this as CompletableFuture<RETURN_TYPE>

    @Throws(Exception::class)
    abstract fun execute(): RETURN_TYPE
}