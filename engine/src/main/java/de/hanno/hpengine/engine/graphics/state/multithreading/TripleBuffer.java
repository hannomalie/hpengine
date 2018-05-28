package de.hanno.hpengine.engine.graphics.state.multithreading;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.state.CustomState;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.StateRef;
import de.hanno.hpengine.util.commandqueue.CommandQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
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
        currentReadState = new QueueStatePair<>(instanceA);
        currentWriteState = new QueueStatePair<>(instanceB);
        currentStagingState = new QueueStatePair<>(instanceC);
        this.instanceA = currentReadState;
        this.instanceB = currentWriteState;
        this.instanceC = currentStagingState;
    }

//    TODO: This must probably happen on the render thread
    public ActionReference registerAction(Action action) {
        int actionIndex = currentReadState.addAction(action);
        currentWriteState.addAction(action);
        currentStagingState.addAction(action);
        return new ActionReference(this, actionIndex);
    }

    public static class ActionReference {
        private TripleBuffer buffer;
        private int index;

        public ActionReference(TripleBuffer buffer, int index) {
            this.buffer = buffer;
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public void request(long cycle) {
            buffer.requestSingletonAction(index, cycle);
        }
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

    public void requestSingletonAction(int i, long cycle) {
        instanceA.actions.get(i).request(cycle);
        instanceB.actions.get(i).request(cycle);
        instanceC.actions.get(i).request(cycle);
    }

    private static class QueueStatePair<T extends RenderState> {
        private final CommandQueue queue = new CommandQueue();
        private final T state;

        private final List<Action> actions = new ArrayList<>();

        public QueueStatePair(T state) {
            this.state = state;
        }

        private int addAction(Action action) {
            this.actions.add(action);
            return actions.size() -1;
        }

        public void addCommand(Consumer<T> command) {
            queue.addCommand(() -> command.accept(state));
        }

        public void update(T writeState) {
            for(int i = 0; i < actions.size(); i++) {
                if(actions.get(i).shouldExecute()) {
                    actions.get(i).consume(state);
                }
            }
            queue.executeCommands();
            this.state.getCustomState().update(writeState);
        }

    }

    public static class Action<EXTRACT> {
        AtomicLong requestedInCycle = new AtomicLong(-1);
        AtomicLong executedInCycle = new AtomicLong(0);
        protected Supplier<EXTRACT> extractor;
        protected BiConsumer<RenderState, EXTRACT> consumer;

        public Action(Supplier<EXTRACT> extractor, BiConsumer<RenderState, EXTRACT> consumer) {
            this.extractor = extractor;
            this.consumer = consumer;
        }

        public boolean shouldExecute() {
            return requestedInCycle.get() > executedInCycle.get();
        }

        public void request(long cycle) {
            requestedInCycle.getAndSet(cycle);
        }

        public void consume(RenderState state) {
            consumer.accept(state, extractor.get());
            executedInCycle.getAndSet(state.getCycle());
        }
    }

    public static class RareAction<EXTRACT> extends Action<EXTRACT> {
        private Engine engine;

        public RareAction(Supplier<EXTRACT> extractor, BiConsumer<RenderState, EXTRACT> consumer, Engine engine) {
            super(extractor, consumer);
            this.engine = engine;
        }
        @Override
        public boolean shouldExecute() {
            return false;
        }
        @Override
        public void request(long cycle) {
            EXTRACT extract = extractor.get();
            engine.getRenderManager().getRenderState().addCommand(state -> consumer.accept(state, extract));
        }

        @Override
        public void consume(RenderState state) {
            consumer.accept(state, extractor.get());
            executedInCycle.getAndSet(state.getCycle());
        }
    }
}
