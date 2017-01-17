package de.hanno.hpengine.util.multithreading;

import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class DoubleBuffer<T> {
    private ReentrantLock swapLock = new ReentrantLock();
    private ReentrantLock readLock = new ReentrantLock();

    private T currentReadState;
    private T currentWriteState;
    private T temp;

    private CommandQueue queueCurrentWriteState = new CommandQueue();
    private CommandQueue queueNextWriteState = new CommandQueue();
    private CommandQueue tempQueue;

    /*
        Represents an instance with two copies of an object, so that the object can
        be read and modified concurrently. The read copy of the object can be
        consumed always, but not while current read and write copies are switched.
        Updates can be done via commands. Commands are recorded in two queues. When
        the doublebuffer is updated, pending commands are executed and the current write
        state is updated. Afterwars, a swap is performed in order to make the just
        updated copy the current read copy. The recorded commands are then executed
        for the current write copy as well.
        Swaps are not performed, when a read is currently in progress. This can be signaled
        with explicitly calling startRead and stopRead. Because reading is the more
        important operation in my eyes, it gets the favour over the swapping action when
        updating the doublebuffer.
     */
    public DoubleBuffer(T instanceA, T instanceB) {
        if(instanceA == null || instanceB == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = instanceA;
        currentWriteState = instanceB;
    }

    protected void swap() {
        synchronized (swapLock) {
            synchronized (readLock) {
                temp = currentReadState;
                currentReadState = currentWriteState;
                currentWriteState = temp;
                temp = null;

                tempQueue = queueCurrentWriteState;
                queueCurrentWriteState = queueNextWriteState;
                queueNextWriteState = tempQueue;
                tempQueue = null;
            }
        }
        update();
    }

    public void addCommand(Consumer<T> command) {
        synchronized (swapLock) {
            queueCurrentWriteState.addCommand(() -> command.accept(getCurrentWriteState()));
            queueNextWriteState.addCommand(() -> command.accept(getCurrentWriteState()));
        }
    }

    public boolean update() {
        synchronized (swapLock) {
            if(readLock.isLocked()) {
                return false;
            }
            if(queueCurrentWriteState.size() > 0) {
                queueCurrentWriteState.executeCommands();
                swap();
            }
            return true;
        }
    }

    public T getCurrentReadState() {
        return currentReadState;
    }
    public T getCurrentWriteState() {
        return currentWriteState;
    }

    public void startRead() {
        readLock.lock();
    }
    public void stopRead() {
        readLock.unlock();
    }
}
