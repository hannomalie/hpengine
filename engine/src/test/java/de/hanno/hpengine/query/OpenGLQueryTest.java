package de.hanno.hpengine.query;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.query.GLTimerQuery;
import de.hanno.hpengine.engine.model.VertexBuffer;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;

import java.util.logging.Logger;

public class OpenGLQueryTest extends TestWithEngine {

    private static final Logger LOGGER = Logger.getLogger(OpenGLQueryTest.class.getName());

    @Test
    public void testTimerQuery() throws InterruptedException {
        GLTimerQuery query = new GLTimerQuery(engine.getGpuContext());

        query.begin();

        int floatValueCount = 300000;
        new VertexBuffer(engine.getGpuContext(), BufferUtils.createFloatBuffer(floatValueCount), ModelComponent.Companion.getPOSITIONCHANNEL()).upload();

        query.end();
        Float result = query.getResult();
        LOGGER.info("Uploading " + floatValueCount + " float values took " + result + " ms");
        Assert.assertTrue("Query should take some timeGpu", result > 1.0);
    }

    @Test(expected = IllegalStateException.class)
    public void testTimerQueryState() {
        GLTimerQuery query = new GLTimerQuery(engine.getGpuContext());

        query.begin();
        query.getResult();
    }
}
