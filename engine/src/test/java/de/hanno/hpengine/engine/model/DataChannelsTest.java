package de.hanno.hpengine;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.VertexBuffer;
import junit.framework.Assert;
import org.junit.Test;

public class DataChannelsTest {

    @Test
    public void testTotalElementsPerVertex() {
        Assert.assertEquals("Total elements per vertex not calculated correctly",
                3, DataChannels.totalElementsPerVertex(ModelComponent.POSITIONCHANNEL));

        Assert.assertEquals("Total elements per vertex not calculated correctly",
                8, DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS));
    }
    @Test
    public void testTotalVerticesCount() {

        int expected = 10;
        float[] floats = new float[DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS)*expected];

        Assert.assertEquals("Total elements per vertex not calculated correctly",
                expected, VertexBuffer.calculateVerticesCount(floats, ModelComponent.DEFAULTCHANNELS));
    }
}
