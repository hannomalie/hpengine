package de.hanno.hpengine.util.commandqueue;

import org.junit.Test;

import java.util.logging.Logger;

public class CommandQueueBenchmark {
    private static final Logger LOGGER = Logger.getLogger(CommandQueueBenchmark.class.getName());

    @Test
    public void benchmarkCommandQueue() throws InterruptedException {

        runForNPosts(1000000);
        runForNPosts(100);
        runForNPosts(1);
    }

    public void runForNPosts(int postCount) {
        CommandQueue commandQueue = new CommandQueue();

        long start = System.currentTimeMillis();
        for(int i = 0; i < postCount; i++) {
            commandQueue.addCommand(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        long durationInMs = System.currentTimeMillis() - start;
        LOGGER.info( commandQueue.getClass().toString() +
                " took " +
                durationInMs +
                " ms to complete " + postCount + " postings (" + (durationInMs / (float) postCount) + " per post)");
    }
}
