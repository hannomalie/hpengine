package de.hanno.hpengine.renderer;

import de.hanno.hpengine.config.Config;
import org.junit.Assert;
import org.junit.Test;

public class GraphicsContextTest {

    @Test
    public void testInitialization() {
        Config.getInstance().setGpuContextClass(MockContext.class);
        Assert.assertTrue(GraphicsContext.getInstance() instanceof MockContext);
    }

}