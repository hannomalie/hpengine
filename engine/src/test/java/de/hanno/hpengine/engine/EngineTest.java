package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.graphics.frame.ApplicationFrame;
import org.junit.Assert;
import org.junit.Test;

public class EngineTest {
    @Test
    public void testSimpleInit() throws Exception {
        ApplicationFrame frame = new ApplicationFrame();
        Engine.init(frame.getRenderCanvas());
        Assert.assertTrue(Engine.getInstance().isInitialized());
    }

}