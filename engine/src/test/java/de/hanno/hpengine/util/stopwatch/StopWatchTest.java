package de.hanno.hpengine.util.stopwatch;

import org.junit.Assert;
import org.junit.Test;

public class StopWatchTest {

    @Test
    public void validation() {
        StopWatch.ACTIVE = true;

        StopWatch stopWatch = new StopWatch();

        stopWatch.start("abc");
        Assert.assertEquals(1, stopWatch.watches.size());
        stopWatch.start("xyz");
        Assert.assertEquals(2, stopWatch.watches.size());
        stopWatch.stop();
        Assert.assertEquals(1, stopWatch.watches.size());
        stopWatch.start("123");
        Assert.assertEquals(2, stopWatch.watches.size());
        stopWatch.stop();
        Assert.assertEquals(1, stopWatch.watches.size());
        stopWatch.stop();
        Assert.assertEquals(0, stopWatch.watches.size());

        StopWatch.ACTIVE = false;
    }
}