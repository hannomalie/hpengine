package de.hanno.hpengine.util.commandqueue;

import java.util.Iterator;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.threads.UpdateThread.isUpdateThread;

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
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    public <RESULT_TYPE> CompletableFuture<RESULT_TYPE> addCommand(Runnable runnable) {
        FutureCallable command = new RunnableCallable(runnable);
        return addCommand(command);
    }
    public <RESULT_TYPE> CompletableFuture<RESULT_TYPE> addCommand(FutureCallable<RESULT_TYPE> command) {
        if(executeDirectly()) {
            try {
                command.getFuture().complete(command.execute());
                return command.getFuture();
            } catch (Exception e) {
                e.printStackTrace();
                command.getFuture().completeExceptionally(e);
                return command.getFuture();
            }
        }
        workQueue.offer(command);
        return command.getFuture();
    }
    public Exception execute(Runnable runnable, boolean andBlock) {
        if(executeDirectly()) {
            runnable.run();
            return null;
        }
        CompletableFuture<Object> future = addCommand(new RunnableCallable(runnable));

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

    private static class RunnableCallable extends FutureCallable<Object> {

        private final Runnable runnable;

        public RunnableCallable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public Object execute() throws Exception {
            runnable.run();
            return null;
        }
    }

    protected boolean executeDirectly() { return false; }
}
