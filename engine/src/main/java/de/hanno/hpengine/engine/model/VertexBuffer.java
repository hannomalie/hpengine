package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.apache.commons.lang.NotImplementedException;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.engine.graphics.buffer.AbstractPersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class VertexBuffer<T extends Bufferable> extends PersistentMappedBuffer<T> {

    static final Logger LOGGER = Logger.getLogger(VertexBuffer.class.getName());

    private transient volatile boolean uploaded = true;
    private int maxLod;
    public enum Usage {
		DYNAMIC(GL15.GL_DYNAMIC_DRAW),
		STATIC(GL15.GL_STATIC_DRAW);

		private int value;

		public int getValue() {
			return value;
		}

		Usage(int usage) {
			this.value = usage;
		}
	}

	private volatile VertexArrayObject vertexArrayObject;
	private int verticesCount;
    private int triangleCount;
	public EnumSet<DataChannels> channels;

    public VertexBuffer(float[] values, EnumSet<DataChannels> channels) {
        super(64, GL15.GL_ARRAY_BUFFER);
        setInternals(values, channels);
	}
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels) {
        super(64, GL15.GL_ARRAY_BUFFER);
        setInternals(buffer, channels);
	}

    public VertexBuffer(FloatBuffer verticesFloatBuffer, EnumSet<DataChannels> channels, IndexBuffer ... indicesBuffer) {
        this(verticesFloatBuffer, channels);
    }

    private void setInternals(FloatBuffer buffer, EnumSet<DataChannels> channels) {
        float[] values = new float[buffer.capacity()];
        buffer.get(values);
        setInternals(values, channels);
    }

    private void setInternals(float[] values, EnumSet<DataChannels> channels) {
        this.channels = channels;
        GraphicsContext.getInstance().execute(() -> {
            bind();
            setVertexArrayObject(VertexArrayObject.getForChannels(channels));
        });
        setCapacityInBytes(values.length * Float.BYTES);
        this.verticesCount = calculateVerticesCount(buffer, channels);
        setMaxLod(1);
        this.triangleCount = verticesCount / 3;
        putValues(values);
    }


	public FloatBuffer buffer(float[] vertices) {
		return buffer(vertices, channels);
	}
	private FloatBuffer buffer(float[] vertices, EnumSet<DataChannels> channels) {

		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		int totalBytesPerVertex = totalElementsPerVertex * Float.BYTES;
        int verticesCount = calculateVerticesCount(vertices, channels);

		for (int i = 0; i < verticesCount; i++) {
			int currentOffset = 0;
			for (DataChannels channel : channels) {
				for(int a = 0; a < channel.getSize(); a++) {
					buffer.putFloat(vertices[(i*totalElementsPerVertex) + currentOffset + a]);
				}
				currentOffset += channel.getSize();
			}
		}

		buffer.rewind();
		return buffer.asFloatBuffer();
	}
	
	public int getVerticesCount() {
//		verticesCount = calculateVerticesCount(channels);
        triangleCount = verticesCount / 3;
		return verticesCount;
	}

	private int calculateVerticesCount(EnumSet<DataChannels> channels) {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		validate(totalElementsPerVertex);
		
		int verticesCount = buffer.capacity() / Float.BYTES / totalElementsPerVertex;
        triangleCount = verticesCount / 3;
		return verticesCount;
	}

    public static int calculateVerticesCount(float[] vertices, EnumSet<DataChannels> channels) {
        int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);

        int verticesCount = vertices.length / totalElementsPerVertex;
        return verticesCount;
    }
    public static int calculateVerticesCount(ByteBuffer floatBuffer, EnumSet<DataChannels> channels) {
        if(floatBuffer == null) {
            return 0;
        }
        floatBuffer.rewind();
        float[] floatArray = new float[floatBuffer.asFloatBuffer().capacity()];
        floatBuffer.asFloatBuffer().get(floatArray);
        return calculateVerticesCount(floatArray, channels);
    }

    public int totalElementsPerVertex() {
		return DataChannels.totalElementsPerVertex(this.channels);
		
	}

	// TODO: Mach irgendwas....
	private void validate(int totalElementsPerVertex) {
		int modulo = buffer.capacity() % totalElementsPerVertex;
		if (modulo != 0) {
			throw new RuntimeException(String.format("Can't buffer those vertices!\n" +
					"vertices count: %d,\n" +
					"Attribute values per vertex: %d\n" +
					"=> Modulo is %d", buffer.capacity(), totalElementsPerVertex, modulo));
		}
	}

    public CompletableFuture<VertexBuffer> upload() {
        VertexBuffer self = this;
        buffer.rewind();
        return GraphicsContext.getInstance().execute(new FutureCallable() {
            @Override
            public VertexBuffer execute() throws Exception {
                bind();
                setVertexArrayObject(VertexArrayObject.getForChannels(channels));
                return self;
            }
        });
    }

    protected ByteBuffer mapBuffer(int capacityInBytes, int flags)  {
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes));
        if(buffer != null) {
            buffer.rewind();
            byteBuffer.put(buffer);
            byteBuffer.rewind();
        }
        return byteBuffer;
    }

    public void delete() {
		GL15.glDeleteBuffers(getId());
        vertexArrayObject.delete();
		buffer = null;
	}

    public int draw() {
        return draw(null);
    }
    public int draw(IndexBuffer indexBuffer) {
        if(!uploaded) { return -1; }
        bind();
        return drawActually(indexBuffer);
    }

    /**
     *
     * @return triangleCount that twas drawn
     */
    private int drawActually(IndexBuffer indexBuffer) {
        LOGGER.finest("drawActually called");
        if(indexBuffer != null) {
            indexBuffer.bind();
            IntBuffer indices = indexBuffer.getBuffer().asIntBuffer();
            GL11.glDrawElements(GL11.GL_TRIANGLES, indices);
            return indices.capacity()/3;
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
            return verticesCount;
        }
    }

    public int drawInstanced(IndexBuffer indexBuffer, int indexCount, int instanceCount, int indexOffset, int baseVertexIndex) {
        if(!uploaded) { return 0; }
        bind();
        if(indexBuffer != null) {
            drawInstancedBaseVertex(indexBuffer, indexCount, instanceCount, indexOffset, baseVertexIndex);
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
        }

        return verticesCount;
    }


    /**
     *
     *
     * @param indexCount
     * @param instanceCount
     * @param indexOffset
     * @param baseVertexIndex the integer index, not the byte offset
     * @return
     */
    public int drawInstancedBaseVertex(IndexBuffer indexBuffer, int indexCount, int instanceCount, int indexOffset, int baseVertexIndex) {
        if(!uploaded) { return 0; }
        bind();
        if(indexBuffer != null) {
            // TODO: use lod
            indexBuffer.bind();
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 4*indexOffset, instanceCount, baseVertexIndex, 0);

        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
        }

        return indexCount;
    }
    public int drawLinesInstancedBaseVertex(IndexBuffer indexBuffer, int indexCount, int instanceCount, int indexOffset, int baseVertexIndex) {
        if(!uploaded) { return 0; }
        bind();
        if(indexBuffer != null) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            GL11.glLineWidth(1f);
            indexBuffer.bind();
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 4*indexOffset, instanceCount, baseVertexIndex, 0);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);

        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
        }

        return indexCount/3;
    }

    public static void drawInstancedIndirectBaseVertex(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, CommandBuffer commandBuffer, int primitiveCount) {
        vertexBuffer.bind();
        // TODO: use lod
        indexBuffer.bind();
        commandBuffer.bind();
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0);//sizeInBytes());
    }
    public static void drawLinesInstancedIndirectBaseVertex(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, CommandBuffer commandBuffer, int primitiveCount) {
        vertexBuffer.bind();
        // TODO: use lod
        indexBuffer.bind();
        commandBuffer.bind();
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glLineWidth(1f);
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0);//sizeInBytes());
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }

    @Override
    public void bind() {
        LOGGER.finest("bind called");
        super.bind();
        if(vertexArrayObject != null) {
            vertexArrayObject.bind();
        }
    }

    @Override
    public void putValues(ByteBuffer values) {
        throw new NotImplementedException();
    }

    @Override
    public void putValues(int offset, ByteBuffer values) {
        throw new NotImplementedException();
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
//        bind();
        setCapacityInBytes((offset + values.length) * Float.BYTES);
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        floatBuffer.position(offset);
        floatBuffer.put(values);
        buffer.rewind();

        int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
        verticesCount = (offset+values.length)/totalElementsPerVertex;
        triangleCount = verticesCount/3;
    }

    @Override
    public void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        setCapacityInBytes(bufferable[0].getBytesPerObject() * bufferable.length);

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            currentBufferable.putToBuffer(buffer);
        }
    }

    public void drawAgain(IndexBuffer indexBuffer) {
        if(!uploaded) { return; }
        drawActually(indexBuffer);
    }

    public int drawDebug() {
        return drawDebug();
    }

    public int drawDebug(IndexBuffer indexBuffer) {
		if(!uploaded) { return 0; }
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(1f);
        bind();
        drawActually(indexBuffer);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        return verticesCount;
	}
    public int drawDebug(IndexBuffer indexBuffer, float lineWidth) {
		if(!uploaded) { return 0; }
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(lineWidth);
		bind();
        drawActually(indexBuffer);
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
		return verticesCount;
	}
	public int drawDebugLines() {
		if(!uploaded) { return 0; }
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(2f);
		bind();
		GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount);
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
		return verticesCount;
	}

    public float[] getVertexData() {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
	
		float[] result = new float[totalElementsPerVertex * verticesCount];
		
		buffer.rewind();
		buffer.asFloatBuffer().get(result);
		return result;
	}
	
	public float[] getValues(DataChannels forChannel) {
		int stride = 0;

		for (DataChannels channel : channels) {
			if (channel.equals(forChannel)) {
				break;
			} else {
				stride += channel.getSize();
			}
		}
		
		int elementCountAfterPositions = totalElementsPerVertex() - (stride + forChannel.getSize());
		
		float[] result = new float[verticesCount * forChannel.getSize()];
		int resultIndex = 0;
		
		int elementsPerChannel = forChannel.getSize();
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        int vertexCounter = 0;
		for (int i = stride; i < floatBuffer.capacity() && vertexCounter < verticesCount; i += stride + elementsPerChannel + elementCountAfterPositions) {
			for (int x = 0; x < forChannel.getSize(); x++) {

                result[resultIndex] = floatBuffer.get(i+x);
				resultIndex++;
			}
            vertexCounter++;
		}
		
		return result;
		
	}

    private void setVertexArrayObject(VertexArrayObject vertexArrayObject) {
        this.vertexArrayObject = vertexArrayObject;
    }

    public int getTriangleCount() {
        return triangleCount;
    }

    public void setMaxLod(int maxLod) {
        this.maxLod = maxLod;
    }

}
