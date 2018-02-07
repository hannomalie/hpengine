package de.hanno.hpengine.engine;

import org.junit.Assert;
import org.junit.Test;

public class EngineTest {
    @Test
    public void testSimpleInit() throws Exception {
        Engine engine = Engine.create();
        Assert.assertTrue(engine.isInitialized());
    }

}