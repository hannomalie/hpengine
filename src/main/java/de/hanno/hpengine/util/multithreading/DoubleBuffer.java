package de.hanno.hpengine.util.multithreading;

import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class DoubleBuffer<T> {
    private final QueueStatePair<T> instanceA;
    private final QueueStatePair<T> instanceB;
    private ReentrantLock swapLock = new ReentrantLock();

    private QueueStatePair<T> currentReadState;
    private QueueStatePair<T> currentWriteState;
    private QueueStatePair<T> temp;

    /*
        Represents an instance with two copies of an object, so that the object can
        be read and modified concurrently. The read copy of the object can be
        consumed always, but not while current read and write copies are switched.
        Updates can be done via commands. Commands are recorded in two queues. When
        the doublebuffer is updated, pending commands are executed and the current write
        state is updated. Afterwards, a swap is performed in order to make the just
        updated copy the current read copy. This only happens when there have been
        some commands to execute at all.
        Swaps are not performed, when a read is currently in progress. This can be signaled
        with explicitly calling startRead and stopRead.
     */
    public DoubleBuffer(T instanceA, T instanceB) {
        if(instanceA == null || instanceB == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = new QueueStatePair<>(instanceA);
        currentWriteState = new QueueStatePair<>(instanceB);
        this.instanceA = currentReadState;
        this.instanceB = currentWriteState;
    }

    protected void swap() {
        swapLock.lock();
        temp = currentReadState;
        currentReadState = currentWriteState;
        currentWriteState = temp;
        swapLock.unlock();
    }

    public void addCommand(Consumer<T> command) {
        instanceA.addCommand(command);
        instanceB.addCommand(command);
    }

    public void addCommandToCurrentWriteQueue(Consumer<T> command) {
        currentWriteState.addCommand(command);
    }

    public void update() {
        if(currentWriteState.queue.executeCommands()) {
            swap();
        }
    }

    public T getCurrentReadState() {
        return currentReadState.state;
    }
    public T getCurrentWriteState() {
        return currentWriteState.state;
    }

    public void startRead() {
        swapLock.lock();
    }
    public void stopRead() {
        swapLock.unlock();
    }

    private static class QueueStatePair<T> {
        private final CommandQueue queue = new CommandQueue();
        private final T state;

        public QueueStatePair(T state) {
            this.state = state;
        }

        public void addCommand(Consumer<T> command) {
            queue.addCommand(() -> command.accept(state));
        }
    }
}
