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
        Assert.assertTrue(commandQueue.executeCommands());

        Assert.assertTrue(!result.isEmpty());
    }

}
