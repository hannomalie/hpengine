package test;

import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Test;

import main.DataChannels;
import main.VertexBuffer;

public class VertexBufferTest {
	
	@Test
	public void correctBuffering() {
		float[] vertexData = new float[] {
		    -1.0f, -1.0f, 0.0f,   0f, 0f,
		    1.0f, -1.0f, 0.0f,    0f, 0f,
		    -1.0f,  1.0f, 0.0f,   0f,  1.0f,
		    -1.0f,  1.0f, 0.0f,   0f,  0f,
		    1.0f, -1.0f, 0.0f,    1.0f, 0f,
		    1.0f,  1.0f, 0.0f,    1.0f,  1.0f
		};
		
		VertexBuffer buffer = new VertexBuffer(vertexData, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
		float[] bufferedData = buffer.getVertexData();
		Assert.assertArrayEquals(vertexData, bufferedData, 0f);
		Assert.assertEquals(6, buffer.getVerticesCount());
	}

	@Test
	public void correctElementsPerVertex() {
		Assert.assertEquals(5, VertexBuffer.totalElementsPerVertex(EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD)));
	}

	@Test
	public void correctBytesPerVertex() {
		Assert.assertEquals(20, VertexBuffer.bytesPerVertex(EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD)));
	}

}
