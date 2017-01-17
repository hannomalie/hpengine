package de.hanno.hpengine.util.multithreading;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DoubleBufferTest {

    @Test
    public void testDoubleBufferConstructor() {
        Object instanceA = new Object();
        Object instanceB = new Object();
        DoubleBuffer doubleBuffer = new DoubleBuffer(instanceA, instanceB);
        Assert.assertEquals(instanceA, doubleBuffer.getCurrentReadState());
        Assert.assertEquals(instanceB, doubleBuffer.getCurrentWriteState());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testDoubleBufferConstructorNullException() {
        new DoubleBuffer(null, null);
    }

    @Test
    public void testSwap() {
        Object instanceA = new Object();
        Object instanceB = new Object();
        DoubleBuffer doubleBuffer = new DoubleBuffer(instanceA, instanceB);
        doubleBuffer.swap();

        Assert.assertEquals(instanceB, doubleBuffer.getCurrentReadState());
        Assert.assertEquals(instanceA, doubleBuffer.getCurrentWriteState());
    }

    @Test
    public void testSwapCountMultithreaded() throws Exception {
        Object instanceA = new Object();
        Object instanceB = new Object();
        final AtomicLong swapCounter = new AtomicLong();
        DoubleBuffer doubleBuffer = new DoubleBuffer(instanceA, instanceB) {
            @Override
            public void swap() {
                swapCounter.getAndIncrement();
                super.swap();
            }
        };

        int threadCount = 6;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for(int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                int counter = 100000;
                for(int currentCounter = 0; currentCounter < counter; currentCounter++) {
                    doubleBuffer.swap();
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(99, TimeUnit.DAYS);
        Assert.assertEquals(instanceA, doubleBuffer.getCurrentReadState());
        Assert.assertEquals(instanceB, doubleBuffer.getCurrentWriteState());
    }

    @Test
    public void testPrepareSwapSinglethreaded() throws InterruptedException {
        AtomicLong instanceA = new AtomicLong();
        AtomicLong instanceB = new AtomicLong();
        DoubleBuffer<AtomicLong> doubleBuffer = new DoubleBuffer(instanceA, instanceB);

        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
        doubleBuffer.update();

        Assert.assertEquals(1, doubleBuffer.getCurrentReadState().get());
    }
    @Test
    public void testPrepareSwapMultithreaded() throws InterruptedException {
        AtomicLong instanceA = new AtomicLong();
        AtomicLong instanceB = new AtomicLong();
        DoubleBuffer<AtomicLong> doubleBuffer = new DoubleBuffer(instanceA, instanceB);

        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());

        int threadCount = 6;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for(int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                int counter = 100;
                for(int currentCounter = 0; currentCounter < counter; currentCounter++) {
                    doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
                    doubleBuffer.update();
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(99, TimeUnit.DAYS);
        Assert.assertEquals(3+600, doubleBuffer.getCurrentReadState().get());
        Assert.assertEquals(3+600, doubleBuffer.getCurrentWriteState().get());
    }

    @Test
    public void testReadBlockedSinglethreaded() throws InterruptedException {
        AtomicLong instanceA = new AtomicLong();
        AtomicLong instanceB = new AtomicLong();
        DoubleBuffer<AtomicLong> doubleBuffer = new DoubleBuffer(instanceA, instanceB);

        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
        doubleBuffer.update();
        Assert.assertEquals(1, doubleBuffer.getCurrentReadState().get());

        doubleBuffer.addCommand((atomicLong) -> atomicLong.getAndIncrement());
        doubleBuffer.startRead();
        Assert.assertEquals(1, doubleBuffer.getCurrentReadState().get());
        boolean updated = doubleBuffer.update();
        Assert.assertFalse(updated);
        doubleBuffer.stopRead();
        updated = doubleBuffer.update();
        Assert.assertTrue(updated);

        Assert.assertEquals(2, doubleBuffer.getCurrentReadState().get());
    }
}
