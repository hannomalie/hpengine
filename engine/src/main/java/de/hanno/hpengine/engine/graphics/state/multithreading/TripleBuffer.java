package de.hanno.hpengine.engine.graphics.state.multithreading;

import de.hanno.hpengine.engine.graphics.state.CustomState;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.StateRef;
import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.concurrent.atomic.AtomicBoolean;
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
    private QueueStatePair<T> tempA;
    private QueueStatePair<T> tempB;

    @SuppressWarnings("unchecked")
    public TripleBuffer(T instanceA, T instanceB, T instanceC, Consumer<T> ... actions) {
        if(instanceA == null || instanceB == null || instanceC == null) {
            throw new IllegalArgumentException("Don't pass null to constructor!");
        }
        currentReadState = new QueueStatePair<>(instanceA, actions);
        currentWriteState = new QueueStatePair<>(instanceB, actions);
        currentStagingState = new QueueStatePair<>(instanceC, actions);
        this.instanceA = currentReadState;
        this.instanceB = currentWriteState;
        this.instanceC = currentStagingState;
    }

    protected boolean swap() {
        boolean swapped = false;
        swapLock.lock();
        stagingLock.lock();

        if(!currentReadState.state.preventSwap(currentStagingState.state, currentReadState.state)) {
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

    public void addCommand(Consumer<T> command) {
        instanceA.addCommand(command);
        instanceB.addCommand(command);
        instanceC.addCommand(command);
    }

    public boolean update() {
        currentWriteState.update(currentWriteState.state);
        swapStaging();
        return true;
    }

    private int customStateCounter = 0;
    public <TYPE extends CustomState> StateRef<TYPE> registerState(Supplier<TYPE> factory) {
        int newIndex = customStateCounter++;
        instanceA.state.add(factory.get());
        instanceB.state.add(factory.get());
        instanceC.state.add(factory.get());
        return new StateRef<>(newIndex);
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
        System.out.println("Read  " + (currentReadState == instanceA ? 0 : (currentReadState == instanceB ? 1 : 2)) + " with cycle " + currentReadState.state.getCycle());
        System.out.println("Stage " + (currentStagingState == instanceA ? 0 : (currentStagingState == instanceB ? 1 : 2)) + " with cycle " + currentStagingState.state.getCycle());
        System.out.println("Write " + (currentWriteState == instanceA ? 0 : (currentWriteState == instanceB ? 1 : 2)) + " with cycle " + currentWriteState.state.getCycle());
    }

    public void requestSingletonAction(int i) {
        instanceA.actionRequested[i].getAndSet(true);
        instanceB.actionRequested[i].getAndSet(true);
        instanceC.actionRequested[i].getAndSet(true);
    }

    private static class QueueStatePair<T extends RenderState> {
        private final CommandQueue queue = new CommandQueue();
        private final T state;

        private final Consumer<T>[] actions;
        private final AtomicBoolean[] actionRequested;

        public QueueStatePair(T state, Consumer<T>[] singletonAction) {
            this.state = state;
            this.actions = singletonAction;
            this.actionRequested = new AtomicBoolean[singletonAction.length];
            for(int i = 0; i < singletonAction.length; i++) {
                this.actionRequested[i] = new AtomicBoolean(false);
            }
        }

        public void addCommand(Consumer<T> command) {
            queue.addCommand(() -> command.accept(state));
        }

        public void update(T writeState) {
            for(int i = 0; i < actions.length; i++) {
                if(actionRequested[i].compareAndSet(true, false)) {
                    actions[i].accept(state);
                }
            }
            queue.executeCommands();
            this.state.getCustomState().update(writeState);
        }
    }

}
