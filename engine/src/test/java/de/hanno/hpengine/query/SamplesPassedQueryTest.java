package de.hanno.hpengine.query;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.graphics.query.GLSamplesPassedQuery;
import junit.framework.Assert;
import org.junit.Test;

import java.util.logging.Logger;

public class SamplesPassedQueryTest extends TestWithEngine {

    private static final Logger LOGGER = Logger.getLogger(SamplesPassedQueryTest.class.getName());

    @Test
    public void testSamplesPassed() throws InterruptedException {
        GLSamplesPassedQuery query = new GLSamplesPassedQuery(engine.getGpuContext());

        query.begin();

        Thread.sleep(10);

        query.end();

        Integer samplesPassedCount = query.getResult();
        LOGGER.info("Samples passed: " + samplesPassedCount);
        Assert.assertTrue(samplesPassedCount > 0);


    }
}
