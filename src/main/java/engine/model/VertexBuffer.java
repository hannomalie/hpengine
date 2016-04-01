package engine.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import renderer.OpenGLContext;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumSet;

public class VertexBuffer {

    private transient volatile boolean uploaded = false;
    private boolean hasIndexBuffer;

    public void setIndexBuffer(IntBuffer indexBuffer) {
        this.indexBuffer = indexBuffer;
        hasIndexBuffer = true;
    }

    public int[] getIndices() {
        int[] dst = new int[indexBuffer.capacity()];
        indexBuffer.get(dst);
        return dst;
    }

    public int getTriangleCount() {
        return triangleCount;
    }

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

    private volatile int vertexBufferName = 0;
    private volatile int indexBufferName = 0;
	private volatile VertexArrayObject vertexArrayObject;
    private FloatBuffer buffer;
    private IntBuffer indexBuffer;
	private int verticesCount;
    private int triangleCount;
	public EnumSet<DataChannels> channels;
	private Usage usage;

	public VertexBuffer(float[] values, EnumSet<DataChannels> channels) {
		this(values, channels, Usage.STATIC);
	}
	public VertexBuffer(float[] values, EnumSet<DataChannels> channels, Usage usage) {
        setInternals(buffer(values, channels), channels, Usage.STATIC);
	}
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels) {
        this(buffer, channels, Usage.STATIC);
	}
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
		setInternals(buffer, channels, usage);
//		buffer.rewind();
	}

    public VertexBuffer(FloatBuffer verticesFloatBuffer, EnumSet<DataChannels> defaultchannels, IntBuffer indicesBuffer) {
        this(verticesFloatBuffer, defaultchannels);
        setIndexBuffer(indicesBuffer);
    }

    private void setInternals(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
        this.buffer = buffer;
        this.channels = channels;
        this.usage = usage;
        this.verticesCount = calculateVerticesCount(buffer, channels);
        this.indexBuffer = BufferUtils.createIntBuffer(verticesCount);
        for (int i = 0; i < verticesCount; i++) {
            indexBuffer.put(i, i);
        }
        setHasIndexBuffer(true);
        this.triangleCount = verticesCount / 3;
    }


	private FloatBuffer buffer(float[] vertices, EnumSet<DataChannels> channels) {
		
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		int totalBytesPerVertex = totalElementsPerVertex * 4;
        int verticesCount = calculateVerticesCount(vertices, channels);

		FloatBuffer buffer = BufferUtils.createFloatBuffer(totalElementsPerVertex * verticesCount);

		for (int i = 0; i < verticesCount; i++) {
			int currentOffset = 0;
			for (DataChannels channel : channels) {
				for(int a = 0; a < channel.getSize(); a++) {
					buffer.put(vertices[(i*totalElementsPerVertex) + currentOffset + a]);
				}
				currentOffset += channel.getSize();
			}
		}

		buffer.rewind();
		return buffer;
	}
	
	public int getVerticesCount() {
		verticesCount = calculateVerticesCount(channels);
        triangleCount = verticesCount / 3;
		return verticesCount;
	}

	private int calculateVerticesCount(EnumSet<DataChannels> channels) {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		validate(totalElementsPerVertex);
		
		int verticesCount = buffer.capacity() / totalElementsPerVertex;
        triangleCount = verticesCount / 3;
		return verticesCount;
	}

    public static int calculateVerticesCount(float[] vertices, EnumSet<DataChannels> channels) {
        int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);

        int verticesCount = vertices.length / totalElementsPerVertex;
        return verticesCount;
    }
    public static int calculateVerticesCount(FloatBuffer floatBuffer, EnumSet<DataChannels> channels) {
        floatBuffer.rewind();
        float[] floatArray = new float[floatBuffer.capacity()];
        floatBuffer.get(floatArray);
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

    public VertexBuffer upload() {
        buffer.rewind();
        OpenGLContext.getInstance().execute(() -> {
            setVertexBufferName(GL15.glGenBuffers());
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferName);
            setVertexArrayObject(VertexArrayObject.getForChannels(channels));

            bind();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, usage.getValue());

            if (hasIndexBuffer) {
                setIndexBufferName(GL15.glGenBuffers());
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBufferName);
                indexBuffer.rewind();
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, usage.getValue());
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            }

        }, true); // TODO: Evaluate if this has to be blocking
        uploaded = true;

        return this;
    }

    public void delete() {
		GL15.glDeleteBuffers(vertexBufferName);
//        vertexArrayObject.delete();
		buffer = null;
	}

    public int draw() {
        if(!uploaded) { return 0; }
        bind();

        if(hasIndexBuffer) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexBuffer);
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
        }

        return verticesCount;
    }

    public void bind() {
        vertexArrayObject.bind();
    }

	public void drawAgain() {
        if(!uploaded) { return; }
        if(hasIndexBuffer) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexBuffer);
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
        }
	}

	public int drawDebug() {
        if(!uploaded) { return 0; }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(1f);
        bind();
        if(hasIndexBuffer) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexBuffer);
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        return verticesCount;
	}

	public int drawInstanced(int instanceCount) {
        if(!uploaded) { return 0; }
        bind();
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
        return verticesCount;
	}

    public float[] getVertexData() {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
	
		float[] result = new float[totalElementsPerVertex * verticesCount];
		
		buffer.rewind();
		buffer.get(result);
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
		for (int i = stride; i < buffer.capacity(); i += stride + elementsPerChannel + elementCountAfterPositions) {
			for (int x = 0; x < forChannel.getSize(); x++) {
				
				result[resultIndex] = buffer.get(i+x);
				resultIndex++;
			}
		}
		
		return result;
		
	}

    private void setVertexBufferName(int vertexBufferName) {
        this.vertexBufferName = vertexBufferName;
    }

    private void setVertexArrayObject(VertexArrayObject vertexArrayObject) {
        this.vertexArrayObject = vertexArrayObject;
    }

    public void setIndexBufferName(int indexBufferName) {
        this.indexBufferName = indexBufferName;
    }

    public void setHasIndexBuffer(boolean hasIndexBuffer) {
        this.hasIndexBuffer = hasIndexBuffer;
    }
}
