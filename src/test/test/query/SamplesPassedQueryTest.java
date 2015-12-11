package test.query;

import engine.graphics.query.GLSamplesPassedQuery;
import junit.framework.Assert;
import org.junit.Test;
import test.TestWithRenderer;

public class SamplesPassedQueryTest extends TestWithRenderer {

    @Test
    public void testSamplesPassed() throws InterruptedException {
        GLSamplesPassedQuery query = new GLSamplesPassedQuery();

        query.begin();

        Thread.sleep(10);

        query.end();

        Integer samplesPassedCount = query.getResult();
        System.out.println("Samples passed: " + samplesPassedCount);
        Assert.assertTrue(samplesPassedCount > 0);


    }
}
