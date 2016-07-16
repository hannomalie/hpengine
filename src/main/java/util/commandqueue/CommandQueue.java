package util.commandqueue;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class CommandQueue {
    private static final Logger LOGGER = Logger.getLogger(CommandQueue.class.getName());

    private BlockingQueue<FutureCallable> workQueue = new LinkedBlockingQueue<>();

    public void executeCommands() {
        while(executeCommand()) {
        }
    }

    public boolean executeCommand() {
        FutureCallable command = workQueue.poll();
        if(command != null) {
            try {
                command.complete(command.execute());
                LOGGER.finer(String.valueOf(workQueue.remainingCapacity()));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    public <RESULT_TYPE extends Object> CompletableFuture<RESULT_TYPE> addCommand(FutureCallable<RESULT_TYPE> command) {
        workQueue.offer(command);
        return command.getFuture();
    }

    public int size() {
        return workQueue.size();
    }

    public Iterator<FutureCallable> getIterator() {
        return workQueue.iterator();
    }
}
