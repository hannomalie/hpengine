package de.hanno.hpengine.util.multithreading;

import de.hanno.hpengine.util.multithreading.DoubleBuffer;
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
    public void testPrepareSwapMultithreaded() throws InterruptedException {
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

}
