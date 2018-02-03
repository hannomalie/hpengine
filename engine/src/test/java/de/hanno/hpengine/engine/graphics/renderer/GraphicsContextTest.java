package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import org.junit.Assert;
import org.junit.Test;

public class GraphicsContextTest {

    @Test
    public void testInitialization() {
        Config.getInstance().setGpuContextClass(MockContext.class);
        Assert.assertTrue(Engine.getInstance().getGpuContext() instanceof MockContext);
    }

}