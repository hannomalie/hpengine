package de.hanno.hpengine.util.commandqueue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CommandQueueTest {

    private CommandQueue commandQueue;
    @Before
    public void setUp() {
        commandQueue = new CommandQueue();
    }

    @Test
    public void executeCommands() throws Exception {
        List result = new ArrayList<>();
        commandQueue.addCommand(() -> result.add(new Object()));
        commandQueue.executeCommands();

        Assert.assertTrue(!result.isEmpty());
    }

    @Test
    public void executeCommand() throws Exception {
        List result = new ArrayList<>();
        commandQueue.addCommand(() -> result.add(new Object()));
        Assert.assertTrue(commandQueue.executeCommands());

        Assert.assertTrue(!result.isEmpty());
    }

    @Test
    public void testFutureResult() throws ExecutionException, InterruptedException {
        CompletableFuture<Integer> future = commandQueue.addCommand(new FutureCallable<Integer>() {
            @Override
            public Integer execute() {
                return 10;
            }
        });

        junit.framework.Assert.assertFalse("The future is already done, even before processes.", future.isDone());
        commandQueue.executeCommands();

        junit.framework.Assert.assertTrue("The future is not completed but should have been.", future.isDone());
        junit.framework.Assert.assertEquals(10, (int) future.get());
    }

}
