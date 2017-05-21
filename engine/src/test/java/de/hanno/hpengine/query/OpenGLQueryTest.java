package de.hanno.hpengine.query;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.query.GLTimerQuery;
import de.hanno.hpengine.engine.model.VertexBuffer;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import de.hanno.hpengine.TestWithOpenGLContext;

import java.util.logging.Logger;

public class OpenGLQueryTest extends TestWithOpenGLContext {

    private static final Logger LOGGER = Logger.getLogger(OpenGLQueryTest.class.getName());

    @Test
    public void testTimerQuery() throws InterruptedException {
        GLTimerQuery query = new GLTimerQuery();

        query.begin();

        int floatValueCount = 300000;
        new VertexBuffer(BufferUtils.createFloatBuffer(floatValueCount), ModelComponent.POSITIONCHANNEL).upload();

        query.end();
        Float result = query.getResult();
        LOGGER.info("Uploading " + floatValueCount + " float values took " + result + " ms");
        Assert.assertTrue("Query should take some time", result > 1.0);
    }

    @Test(expected = IllegalStateException.class)
    public void testTimerQueryState() {
        GLTimerQuery query = new GLTimerQuery();

        query.begin();
        query.getResult();
    }
}
