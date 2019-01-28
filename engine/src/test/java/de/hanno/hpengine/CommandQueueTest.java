package de.hanno.hpengine;

import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CommandQueueTest {

    private static CommandQueue commandQueue;

    @BeforeClass
    public static void init() {
        commandQueue = new CommandQueue();
    }

    @Test
    public void testFutureResult() throws ExecutionException, InterruptedException {
        CompletableFuture<SimpleResult> future = commandQueue.addCommand(new FutureCallable<SimpleResult>() {
            @Override
            public SimpleResult execute() {
                SimpleResult result = new SimpleResult();
                result.x = 10;
                return result;
            }
        });

        Assert.assertFalse("The future is already done, even before processes.", future.isDone());
        commandQueue.executeCommand();

        Assert.assertTrue("The future is not completed but should have been.", future.isDone());
        Assert.assertEquals(10, future.get().x);
    }

    private static class SimpleResult {
        public int x = 0;
    }

}
