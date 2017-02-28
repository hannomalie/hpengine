package de.hanno.hpengine.util.commandqueue;

import java.util.Iterator;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class CommandQueue {
    private static final Logger LOGGER = Logger.getLogger(CommandQueue.class.getName());

    private ConcurrentLinkedQueue<FutureCallable> workQueue = new ConcurrentLinkedQueue<>();

    public boolean executeCommands() {
        boolean executedCommands = false;
        while(executeCommand()) {
            executedCommands = true;
        }
        return executedCommands;
    }

    public boolean executeCommand() {
        FutureCallable command = workQueue.poll();
        if(command != null) {
            try {
                command.complete(command.execute());
                LOGGER.finer(String.valueOf(workQueue.size()));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    public <RESULT_TYPE extends Object> CompletableFuture<RESULT_TYPE> addCommand(Runnable runnable) {
        FutureCallable command = new FutureCallable() {
            @Override
            public Object execute() throws Exception {
                try {
                    runnable.run();
                    complete(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    completeExceptionally(e);
                }
                return null;
            }
        };
        workQueue.offer(command);
        return command.getFuture();
    }
    public <RESULT_TYPE extends Object> CompletableFuture<RESULT_TYPE> addCommand(FutureCallable<RESULT_TYPE> command) {
        workQueue.offer(command);
        return command.getFuture();
    }
    public Exception execute(Runnable runnable, boolean andBlock) {
        CompletableFuture<Object> future = addCommand(new FutureCallable<Object>() {

            @Override
            public Object execute() throws Exception {
                runnable.run();
                return null;
            }
        });

        if(andBlock) {
            future.join();
        }
        return null;
    }
    public int size() {
        return workQueue.size();
    }

    public Iterator<FutureCallable> getIterator() {
        return workQueue.iterator();
    }

    public ConcurrentLinkedQueue<FutureCallable> getWorkQueue() {
        return workQueue;
    }
}
