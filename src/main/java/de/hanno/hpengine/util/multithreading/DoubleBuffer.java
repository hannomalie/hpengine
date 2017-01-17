package de.hanno.hpengine.util.multithreading;

import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.concurrent.locks.ReentrantLock;

public class DoubleBuffer<T> {
    private ReentrantLock swapLock = new ReentrantLock();

    private T currentReadState;
    private T currentWriteState;
    private T temp;

    private CommandQueue queueCurrentWriteState = new CommandQueue();
    private CommandQueue queueNextWriteState = new CommandQueue();

    public DoubleBuffer(T instanceA, T instanceB) {
        if(instanceA == null || instanceB == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = instanceA;
        currentWriteState = instanceB;
    }

    public void swap() {
        synchronized (swapLock) {
            temp = currentReadState;
            currentReadState = currentWriteState;
            currentWriteState = temp;
            temp = null;
        }
    }

    public void update() {
        queueCurrentWriteState.executeCommands();
    }

    public T getCurrentReadState() {
        return currentReadState;
    }
    public T getCurrentWriteState() {
        return currentWriteState;
    }

}
