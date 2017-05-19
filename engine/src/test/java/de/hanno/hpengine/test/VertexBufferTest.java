package de.hanno.hpengine.test;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.query.GLTimerQuery;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.GraphicsContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.EnumSet;

public class VertexBufferTest extends TestWithEngine {

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
        float[] vertexData2 = new float[] {
                -91.0f, -1.0f, 0.0f,   0f, 0f,
                1.0f, -1.0f, 0.0f,    0f, 0f,
                -1.0f,  1.0f, 0.0f,   0f,  1.0f,
                -1.0f,  1.0f, 0.0f,   0f,  0f,
                1.0f, -1.0f, 0.0f,    1.0f, 0f,
                1.0f,  1.0f, 0.0f,    1.0f,  1.0f,
                1.0f, -1.0f, 0.0f,    1.0f, 0f,
                1.0f,  1.0f, 0.0f,    1.0f,  1.0f
        };

        VertexBuffer buffer = new VertexBuffer(vertexData, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
        buffer.upload();
        float[] bufferedData = buffer.getVertexData();
        Assert.assertArrayEquals(vertexData, bufferedData, 0f);
        Assert.assertEquals(6, buffer.getVerticesCount());

        buffer.putValues(vertexData2);
        buffer.upload();
        float[] bufferedData2 = buffer.getVertexData();
        for(int i = 0; i < vertexData2.length; i++) {
            Assert.assertTrue("Vertexdata " + i + " was wrong", vertexData2[i] == bufferedData2[i]);
        }

        buffer.putValues(vertexData2.length, vertexData2);
        buffer.upload();
        float[] bufferedData3 = buffer.getVertexData();
        for(int i = 0; i < vertexData2.length+vertexData2.length; i++) {
            Assert.assertTrue("Vertexdata " + i + " was wrong", vertexData2[i%vertexData2.length] == bufferedData3[i]);
        }
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

    @Ignore
    @Test
    public void benchmarkBufferUpload() {
        int floatBufferSize = 8*10000000;
        VertexBuffer buffer = new VertexBuffer(BufferUtils.createFloatBuffer(floatBufferSize), ModelComponent.DEFAULTCHANNELS);

        System.out.println("Starting VertexBufferUpload");
        long start = System.currentTimeMillis();
        buffer.upload();
        System.out.println("Buffer upload of " + floatBufferSize + " took " + (System.currentTimeMillis() - start) + "ms");

    }

    @Test
    public void indexBufferHasCorrectIndices() {
        float[] vertexData = new float[] {
                -1.0f, -1.0f, 0.0f,   0f, 0f,
                1.0f, -1.0f, 0.0f,    0f, 0f,
                -1.0f,  1.0f, 0.0f,   0f,  1.0f,
                -1.0f,  1.0f, 0.0f,   0f,  0f,
                1.0f, -1.0f, 0.0f,    1.0f, 0f,
                1.0f,  1.0f, 0.0f,    1.0f,  1.0f
        };

        VertexBuffer buffer = new VertexBuffer(vertexData, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
        buffer.upload();
        Assert.assertEquals(6, buffer.getVerticesCount());
        Assert.assertEquals(2, buffer.getTriangleCount());

    }

    @Test
    @Ignore
    public void benchmarkVAOAndVBB() {
        int count = 100000000;

        GraphicsContext.getInstance().execute(() -> {
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BufferUtils.createFloatBuffer(30), VertexBuffer.Usage.STATIC.getValue());

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, 4, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 4, 4);

            GLTimerQuery.getInstance().begin();

            for(int i = 0; i < 10000000; i++) {
                GL30.glBindVertexArray(vao);
            }
            GLTimerQuery.getInstance().end();
            System.out.println(GLTimerQuery.getInstance().getResult());
        });

        GraphicsContext.getInstance().execute(() -> {
            VertexBuffer buffer = new VertexBuffer(BufferUtils.createFloatBuffer(30), ModelComponent.POSITIONCHANNEL);
            buffer.upload();

            GLTimerQuery.getInstance().begin();
            for(int i = 0; i < count; i++) {
                buffer.bind();
            }
            GLTimerQuery.getInstance().end();
            System.out.println("VB bind" + GLTimerQuery.getInstance().getResult());
        });

        GraphicsContext.getInstance().execute(() -> {
            VertexBuffer buffer = new VertexBuffer(BufferUtils.createFloatBuffer(30), ModelComponent.POSITIONCHANNEL);
            buffer.upload();

            GLTimerQuery.getInstance().begin();
            for(int i = 0; i < count; i++) {
                buffer.draw();
            }
            GLTimerQuery.getInstance().end();
            System.out.println("VB draw " + GLTimerQuery.getInstance().getResult());
        });
    }
}
