package test.query;

import engine.graphics.query.GLSamplesPassedQuery;
import junit.framework.Assert;
import org.junit.Test;
import test.TestWithRenderer;

import java.util.logging.Logger;

public class SamplesPassedQueryTest extends TestWithRenderer {

    private static final Logger LOGGER = Logger.getLogger(SamplesPassedQueryTest.class.getName());

    @Test
    public void testSamplesPassed() throws InterruptedException {
        GLSamplesPassedQuery query = new GLSamplesPassedQuery();

        query.begin();

        Thread.sleep(10);

        query.end();

        Integer samplesPassedCount = query.getResult();
        LOGGER.info("Samples passed: " + samplesPassedCount);
        Assert.assertTrue(samplesPassedCount > 0);


    }
}
