package de.hanno.hpengine.util.commandqueue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        Assert.assertTrue(commandQueue.executeCommand());

        Assert.assertTrue(!result.isEmpty());
    }

    @Test
    public void addCommand() throws Exception {
        List result = new ArrayList<>();
        CompletableFuture<Object> future = commandQueue.addCommand(() -> result.add(new Object()));
        Assert.assertEquals("", 1, commandQueue.getWorkQueue().size());
        commandQueue.executeCommands();
        Assert.assertNotNull(future.get());
    }

    @Test
    public void addCommand1() throws Exception {
        List result = new ArrayList<>();
        commandQueue.addCommand(() -> result.add(new Object()));
        Assert.assertEquals("", 1, commandQueue.getWorkQueue().size());
    }

    @Test
    public void execute() throws Exception {
        List result = new ArrayList<>();
        Exception exception = commandQueue.execute(() -> result.add(new Object()), false);
        commandQueue.executeCommands();
        Assert.assertNull(exception);
    }

}
