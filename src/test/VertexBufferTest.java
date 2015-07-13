package test;

import engine.model.DataChannels;
import engine.model.VertexBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector4f;

import java.util.EnumSet;

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
	
	@Test
	public void getValues() {
		float[] vertexData = new float[] {
		    -1.0f, -1.0f, 0.0f,   0f, 0f,
		    1.0f, -1.0f, 0.0f,    0f, 0f,
		    -1.0f,  1.0f, 0.0f,   0f,  1.0f,
		    -1.0f,  1.0f, 0.0f,   0f,  0f,
		    1.0f, -1.0f, 0.0f,    1.0f, 0f,
		    1.0f,  1.0f, 0.0f,    1.0f,  1.0f
		};
		
		VertexBuffer buffer = new VertexBuffer(vertexData, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
		float[] result = buffer.getValues(DataChannels.TEXCOORD);
		Assert.assertArrayEquals(new float[]{0f, 0f, 0f, 0f, 0f, 1.0f, 0f, 0f, 1.0f, 0f, 1.0f, 1.0f}, result, 0f);
	}
	
	@Test
	public void minMax() {
		float[] vertexData = new float[] {
		    -1.0f, -1.0f, 0.0f,   0f, 0f,
		    1.0f, -1.0f, 0.0f,    0f, 0f,
		    -1.0f,  1.0f, 0.0f,   0f,  1.0f,
		    -1.0f,  1.0f, 0.0f,   0f,  0f,
		    1.0f, -1.0f, 0.0f,    1.0f, 0f,
		    1.0f,  1.0f, 0.0f,    1.0f,  1.0f
		};
		
		VertexBuffer buffer = new VertexBuffer(vertexData, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
		
		Vector4f[] minMax = buffer.getMinMax();

		Assert.assertEquals(new Vector4f(-1, -1, 0, 1), minMax[0]);
		Assert.assertEquals(new Vector4f(1, 1, 0, 1), minMax[1]);
	}


}
