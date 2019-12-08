package de.hanno.hpengine.engine.graphics.state.multithreading;

import de.hanno.hpengine.engine.graphics.state.CustomState;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.StateRef;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TripleBuffer<T extends RenderState> {
    private static final Logger LOGGER = Logger.getLogger(TripleBuffer.class.getName());

    private final T instanceA;
    private final T instanceB;
    private final T instanceC;

    private ReentrantLock swapLock = new ReentrantLock();
    private ReentrantLock stagingLock = new ReentrantLock();

    private T currentReadState;
    private T currentWriteState;
    private T currentStagingState;
    private T tempA;
    private T tempB;

    @SuppressWarnings("unchecked")
    public TripleBuffer(T instanceA, T instanceB, T instanceC, Consumer<T> ... actions) {
        if(instanceA == null || instanceB == null || instanceC == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = instanceA;
        currentWriteState = instanceB;
        currentStagingState = instanceC;
        this.instanceA = currentReadState;
        this.instanceB = currentWriteState;
        this.instanceC = currentStagingState;
    }

    protected boolean swap() {
        boolean swapped = false;
        swapLock.lock();
        stagingLock.lock();

        if(!currentReadState.preventSwap(currentStagingState, currentReadState))
        {
            tempA = currentReadState;
            currentReadState = currentStagingState;
            currentStagingState = tempA;
            swapped = true;
        }

        stagingLock.unlock();
        swapLock.unlock();
        return swapped;
    }
    protected void swapStaging() {
        stagingLock.lock();

        tempB = currentStagingState;
        currentStagingState = currentWriteState;
        currentWriteState = tempB;

        stagingLock.unlock();
    }

    public boolean update() {
        currentWriteState.getCustomState().update(currentWriteState);
        swapStaging();
        return true;
    }

    private int customStateCounter = 0;
    public <TYPE extends CustomState> StateRef<TYPE> registerState(Supplier<TYPE> factory) {
        int newIndex = customStateCounter++;
        instanceA.add(factory.get());
        instanceB.add(factory.get());
        instanceC.add(factory.get());
        return new StateRef<>(newIndex);
    }

    public T getCurrentReadState() {
        return currentReadState;
    }
    public T getCurrentWriteState() {
        return currentWriteState;
    }

    public void startRead() {
        swapLock.lock();
    }

    public boolean stopRead() {
        return swap();
    }

    public void logState() {
        if(LOGGER.isLoggable(Level.FINER)) {
            LOGGER.fine("Read  " + (currentReadState == instanceA ? 0 : (currentReadState == instanceB ? 1 : 2)));
            LOGGER.fine("Stage " + (currentStagingState == instanceA ? 0 : (currentStagingState == instanceB ? 1 : 2)));
            LOGGER.fine("Write " + (currentWriteState == instanceA ? 0 : (currentWriteState == instanceB ? 1 : 2)));
        }
    }
    public void printState() {
        System.out.println("Read  " + (currentReadState == instanceA ? 0 : (currentReadState == instanceB ? 1 : 2)) + " with cycle " + currentReadState.getCycle());
        System.out.println("Stage " + (currentStagingState == instanceA ? 0 : (currentStagingState == instanceB ? 1 : 2)) + " with cycle " + currentStagingState.getCycle());
        System.out.println("Write " + (currentWriteState == instanceA ? 0 : (currentWriteState == instanceB ? 1 : 2)) + " with cycle " + currentWriteState.getCycle());
    }

    private static class State<T extends RenderState> {
        private final T state;

        public State(T state) {
            this.state = state;
        }
    }

}
