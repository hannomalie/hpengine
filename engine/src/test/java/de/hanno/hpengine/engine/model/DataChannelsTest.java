package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.component.ModelComponent;
import junit.framework.Assert;
import org.junit.Test;

public class DataChannelsTest {

    @Test
    public void testTotalElementsPerVertex() {
        Assert.assertEquals("Total elements per vertex not calculated correctly",
                3, DataChannels.totalElementsPerVertex(ModelComponent.Companion.getPOSITIONCHANNEL()));

        Assert.assertEquals("Total elements per vertex not calculated correctly",
                8, DataChannels.totalElementsPerVertex(ModelComponent.Companion.getDEFAULTCHANNELS()));
    }
    @Test
    public void testTotalVerticesCount() {

        int expected = 10;
        float[] floats = new float[DataChannels.totalElementsPerVertex(ModelComponent.Companion.getDEFAULTCHANNELS())*expected];

        Assert.assertEquals("Total elements per vertex not calculated correctly",
                expected, VertexBuffer.Companion.calculateVerticesCount(floats, ModelComponent.Companion.getDEFAULTCHANNELS()));
    }
}
