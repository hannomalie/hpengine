package de.hanno.hpengine.util.commandqueue;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public abstract class FutureCallable<RETURN_TYPE> extends CompletableFuture<Callable<RETURN_TYPE>> {

    public final CompletableFuture<RETURN_TYPE> getFuture() {
        return (CompletableFuture<RETURN_TYPE>) this;
    }

    public abstract RETURN_TYPE execute() throws Exception;

}
