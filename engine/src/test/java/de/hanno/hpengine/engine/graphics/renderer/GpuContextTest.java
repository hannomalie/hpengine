package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.GpuContext;
import org.junit.Assert;
import org.junit.Test;

public class GpuContextTest {

    @Test
    public void testInitialization() {
        Config.getInstance().setGpuContextClass(MockContext.class);
        Assert.assertTrue(GpuContext.create() instanceof MockContext);
    }

}