package de.hanno.hpengine;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.query.GLTimerQuery;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.VertexBufferKt;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
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

        VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD), vertexData);
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
        class Vertex implements Bufferable{
            Vector3f position = new Vector3f();
            Vector2f texCoord = new Vector2f();

            public Vertex(Vector3f position, Vector2f texCoord) {
                this.position = position;
                this.texCoord = texCoord;
            }

            public Vertex() {
            }

            @Override
            public void putToBuffer(ByteBuffer buffer) {
                buffer.putFloat(position.x);
                buffer.putFloat(position.y);
                buffer.putFloat(position.z);
                buffer.putFloat(texCoord.x);
                buffer.putFloat(texCoord.y);
            }

            @Override
            public int getBytesPerObject() {
                return Float.BYTES * 5;
            }
            @Override
            public void getFromBuffer(ByteBuffer buffer) {
                position.x = buffer.getFloat();
                position.y = buffer.getFloat();
                position.z = buffer.getFloat();
                texCoord.x = buffer.getFloat();
                texCoord.y = buffer.getFloat();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Vertex vertex = (Vertex) o;

                if (!position.equals(vertex.position)) return false;
                return texCoord.equals(vertex.texCoord);
            }

            @Override
            public int hashCode() {
                int result = position.hashCode();
                result = 31 * result + texCoord.hashCode();
                return result;
            }
        }
		float[] vertexData = new float[] {
		    -1.0f, -1.0f, 0.0f,   0f, 0f,
		    1.0f, -1.0f, 0.0f,    0f, 0f,
		    -1.0f,  1.0f, 0.0f,   0f,  1.0f,
		    -1.0f,  1.0f, 0.0f,   0f,  0f,
		    1.0f, -1.0f, 0.0f,    1.0f, 0f,
		    1.0f,  1.0f, 0.0f,    1.0f,  1.0f
		};
		
		VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD), vertexData);
		float[] result = buffer.getValues(DataChannels.TEXCOORD);
		Assert.assertArrayEquals(new float[]{0f, 0f, 0f, 0f, 0f, 1.0f, 0f, 0f, 1.0f, 0f, 1.0f, 1.0f}, result, 0f);

		Vertex current = new Vertex();
		current.getFromBuffer(buffer.getBuffer());
        Assert.assertEquals(new Vertex(new Vector3f(-1, -1, 0), new Vector2f(0,0)), current);
        buffer.getBuffer().position(current.getBytesPerObject());
        current.getFromBuffer(buffer.getBuffer());
        Assert.assertEquals(new Vertex(new Vector3f(1, -1, 0), new Vector2f(0,0)), current);
        buffer.getBuffer().position(5*current.getBytesPerObject());
        current.getFromBuffer(buffer.getBuffer());
        Assert.assertEquals(new Vertex(new Vector3f(1, 1, 0), new Vector2f(1,1)), current);

	}

    @Ignore
    @Test
    public void benchmarkBufferUpload() {
        int floatBufferSize = 8*10000000;
        VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), BufferUtils.createFloatBuffer(floatBufferSize), ModelComponent.Companion.getDEFAULTCHANNELS());

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

        VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD), vertexData);
        buffer.upload();
        Assert.assertEquals(6, buffer.getVerticesCount());
        Assert.assertEquals(2, buffer.getTriangleCount());

    }

    @Test
    @Ignore
    public void benchmarkVAOAndVBB() {
        int count = 100000000;

        engine.getGpuContext().execute(() -> {
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BufferUtils.createFloatBuffer(30), VertexBuffer.Usage.STATIC.getValue());

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, 4, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 4, 4);

            GLTimerQuery query = new GLTimerQuery(engine.getGpuContext()).begin();

            for(int i = 0; i < 10000000; i++) {
                GL30.glBindVertexArray(vao);
            }
            query.end();
            System.out.println(query.getResult());
        });

        engine.getGpuContext().execute(() -> {
            VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), BufferUtils.createFloatBuffer(30), ModelComponent.Companion.getPOSITIONCHANNEL());
            buffer.upload();

            GLTimerQuery query = new GLTimerQuery(engine.getGpuContext()).begin();
            for(int i = 0; i < count; i++) {
                buffer.bind();
            }
            query.end();
            System.out.println("VB bind" + query.getResult());
        });

        engine.getGpuContext().execute(() -> {
            VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), BufferUtils.createFloatBuffer(30), ModelComponent.Companion.getPOSITIONCHANNEL());
            buffer.upload();

            GLTimerQuery query = new GLTimerQuery(engine.getGpuContext()).begin();
            for(int i = 0; i < count; i++) {
                VertexBufferKt.draw(buffer);
            }
            query.end();
            System.out.println("VB draw " + query.getResult());
        });
    }
}
