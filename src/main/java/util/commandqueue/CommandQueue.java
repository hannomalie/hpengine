package util.commandqueue;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandQueue {
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
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
//            System.out.println("#################################");
//            System.out.println(workQueue.size());
//            workQueue.forEach(futureCallable -> System.out.println(futureCallable));
        } else {
            return false;
        }
    }

    public <RESULT_TYPE extends Object> CompletableFuture<RESULT_TYPE> addCommand(FutureCallable<RESULT_TYPE> command) {
        workQueue.offer(command);
        return command.getFuture();
    }

    public Iterator<FutureCallable> getIterator() {
        return workQueue.iterator();
    }
}
