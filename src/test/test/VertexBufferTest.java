package test;

import component.ModelComponent;
import engine.model.DataChannels;
import engine.model.VertexBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;

import java.util.EnumSet;

public class VertexBufferTest extends TestWithOpenGLContext {
	
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
		Assert.assertEquals(5, DataChannels.totalElementsPerVertex(EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD)));
	}

	@Test
	public void correctBytesPerVertex() {
		Assert.assertEquals(20, DataChannels.bytesPerVertex(EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD)));
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
    public void benchmarkBufferUpload() {
        int floatBufferSize = 8*10000000;
        VertexBuffer buffer = new VertexBuffer(BufferUtils.createFloatBuffer(floatBufferSize), ModelComponent.DEFAULTCHANNELS);

        System.out.println("Starting VertexBufferUpload");
        long start = System.currentTimeMillis();
        buffer.upload();
        System.out.println("Buffer upload of " + floatBufferSize + " took " + (System.currentTimeMillis() - start) + "ms");

    }

    //TODO: Create MinMaxTest for model
}
