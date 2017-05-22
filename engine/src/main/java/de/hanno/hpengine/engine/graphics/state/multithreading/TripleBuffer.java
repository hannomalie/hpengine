package de.hanno.hpengine.engine.graphics.state.multithreading;

import de.hanno.hpengine.engine.graphics.renderer.Pipeline;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TripleBuffer<T extends RenderState> {
    private static final Logger LOGGER = Logger.getLogger(TripleBuffer.class.getName());

    private final QueueStatePair<T> instanceA;
    private final QueueStatePair<T> instanceB;
    private final QueueStatePair<T> instanceC;

    private ReentrantLock swapLock = new ReentrantLock();
    private ReentrantLock stagingLock = new ReentrantLock();

    private QueueStatePair<T> currentReadState;
    private QueueStatePair<T> currentWriteState;
    private QueueStatePair<T> currentStagingState;
    private QueueStatePair<T> temp;

    public TripleBuffer(T instanceA, T instanceB, T instanceC) {
        if(instanceA == null || instanceB == null || instanceC == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = new QueueStatePair<>(instanceA);
        currentWriteState = new QueueStatePair<>(instanceB);
        currentStagingState = new QueueStatePair<>(instanceC);
        this.instanceA = currentReadState;
        this.instanceB = currentWriteState;
        this.instanceC = currentStagingState;
    }

    protected void swap() {
        swapLock.lock();
        stagingLock.lock();

        if(!currentReadState.state.preventSwap(currentStagingState.state, currentReadState.state)) {
            temp = currentReadState;
            currentReadState = currentStagingState;
            currentStagingState = temp;
        }

        stagingLock.unlock();
        swapLock.unlock();
    }
    protected void swapStaging() {
        stagingLock.lock();

        temp = currentStagingState;
        currentStagingState = currentWriteState;
        currentWriteState = temp;

        stagingLock.unlock();
    }

    public void addCommand(Consumer<T> command) {
        instanceA.addCommand(command);
        instanceB.addCommand(command);
        instanceC.addCommand(command);
    }

    public boolean update() {
        currentWriteState.queue.executeCommands();
        preparePipelines();
        swapStaging();
        return true;
    }

    public T getCurrentReadState() {
        return currentReadState.state;
    }
    public T getCurrentWriteState() {
        return currentWriteState.state;
    }

    public void startRead() {
        swapLock.lock();
        logState();
    }

    public void stopRead() {
        swap();
    }

    public void logState() {
        if(LOGGER.isLoggable(Level.FINER)) {
            LOGGER.fine("Read  " + (currentReadState == instanceA ? 0 : (currentReadState == instanceB ? 1 : 2)));
            LOGGER.fine("Stage " + (currentStagingState == instanceA ? 0 : (currentStagingState == instanceB ? 1 : 2)));
            LOGGER.fine("Write " + (currentWriteState == instanceA ? 0 : (currentWriteState == instanceB ? 1 : 2)));
        }
    }
    public void printState() {
        System.out.println("Read  " + (currentReadState == instanceA ? 0 : (currentReadState == instanceB ? 1 : 2)) + " with cycle " + currentReadState.state.getCycle());
        System.out.println("Stage " + (currentStagingState == instanceA ? 0 : (currentStagingState == instanceB ? 1 : 2)) + " with cycle " + currentStagingState.state.getCycle());
        System.out.println("Write " + (currentWriteState == instanceA ? 0 : (currentWriteState == instanceB ? 1 : 2)) + " with cycle " + currentWriteState.state.getCycle());
    }

    public int registerPipeline(Supplier<Pipeline> supplier) {
        Pipeline a = supplier.get();
        Pipeline b = supplier.get();
        Pipeline c = supplier.get();
        if(a == b || a == c || b == c) {
            throw new IllegalArgumentException("Supplier has to provide new instances each time it is called!");
        }
        int aInt = instanceA.state.addPipeline(a);
        int bInt = instanceB.state.addPipeline(b);
        int cInt = instanceC.state.addPipeline(c);
        if(aInt != bInt || aInt != cInt || bInt != cInt) {
            throw new IllegalStateException("Should get the same index for all three states for a single pipeline!");
        }
        return aInt;
    }

    public void preparePipelines() {
        for(Pipeline currentPipeline : currentWriteState.state.getPipelines()) {
            currentPipeline.prepare(currentWriteState.state);
        }
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
