package engine.model;

import org.apache.commons.lang.NotImplementedException;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import renderer.OpenGLContext;
import shader.AbstractPersistentMappedBuffer;
import shader.Bufferable;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class VertexBuffer extends AbstractPersistentMappedBuffer<FloatBuffer> {

    private transient volatile boolean uploaded = false;
    private boolean hasIndexBuffer;

    public void setIndexBuffers(List<IntBuffer> indexBuffers) {
        this.indexBuffers = indexBuffers;
        hasIndexBuffer = true;
    }

    public int[] getIndices() {
        int[] dst = new int[indexBuffers.get(0).capacity()];
        indexBuffers.get(0).get(dst);
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

    private volatile int[] indexBufferNames;
	private volatile VertexArrayObject vertexArrayObject;
    private List<IntBuffer> indexBuffers = new ArrayList<>();
	private int verticesCount;
    private int triangleCount;
	public EnumSet<DataChannels> channels;
	private Usage usage;

	public VertexBuffer(float[] values, EnumSet<DataChannels> channels) {
        super(GL15.GL_ARRAY_BUFFER);
        setInternals(values, channels, Usage.STATIC);
	}
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels) {
        this(buffer, channels, Usage.STATIC);
	}
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
        super(GL15.GL_ARRAY_BUFFER);
        setInternals(buffer, channels, usage);
	}

    public VertexBuffer(FloatBuffer verticesFloatBuffer, EnumSet<DataChannels> channels, IntBuffer ... indicesBuffer) {
        this(verticesFloatBuffer, channels);
        setIndexBuffers(Arrays.asList(indicesBuffer));
    }

    private void setInternals(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
        float[] values = new float[buffer.capacity()];
        buffer.get(values);
        setInternals(values, channels, usage);
    }
    private void setInternals(float[] values, EnumSet<DataChannels> channels, Usage usage) {
        this.channels = channels;
        setCapacityInBytes(values.length*getPrimitiveSizeInBytes());
        OpenGLContext.getInstance().execute(() -> {
            setId(GL15.glGenBuffers());
            bind();
            setVertexArrayObject(VertexArrayObject.getForChannels(channels));
        });
        this.usage = usage;
        this.verticesCount = calculateVerticesCount(buffer, channels);
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(verticesCount);
        this.indexBuffers.add(indexBuffer);
        for (int i = 0; i < verticesCount; i++) {
            indexBuffer.put(i, i);
        }
        setHasIndexBuffer(true);
        this.triangleCount = verticesCount / 3;
        putValues(values);
    }


	public FloatBuffer buffer(float[] vertices) {
		return buffer(vertices, channels);
	}
	private FloatBuffer buffer(float[] vertices, EnumSet<DataChannels> channels) {

		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		int totalBytesPerVertex = totalElementsPerVertex * getPrimitiveSizeInBytes();
        int verticesCount = calculateVerticesCount(vertices, channels);

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
//        System.out.println("upload " + isMapped());
        buffer.rewind();
		uploaded = false;
        OpenGLContext.getInstance().execute(() -> {
            bind();
            setVertexArrayObject(VertexArrayObject.getForChannels(channels));
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, usage.getValue());

            if (hasIndexBuffer) {
                int indexBufferIndex = 0;
                indexBufferNames = new int[indexBuffers.size()];
                for(IntBuffer indexBuffer : indexBuffers) {
                    setIndexBufferName(indexBufferIndex, GL15.glGenBuffers());
                    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBufferNames[indexBufferIndex]);
                    indexBuffer.rewind();
                    GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, usage.getValue());
                    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
                    indexBufferIndex++;
                }
            }

            uploaded = true;
        }, false); // TODO: Evaluate if this has to be blocking

        return this;
    }

    protected FloatBuffer mapBuffer(int capacityInBytes, int flags)  {
        return glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes)).asFloatBuffer();
    }

    private void setIndexBufferName(int indexBufferIndex, int indexBufferName) {
        indexBufferNames[indexBufferIndex] = indexBufferName;
    }

    public void delete() {
		GL15.glDeleteBuffers(getId());
        vertexArrayObject.delete();
		buffer = null;
	}

    public int draw() {
        return draw(0);
    }
    public int draw(int lodLevel) {
        if(!uploaded) { return 0; }
        bind();
        return drawActually(lodLevel);
    }

    private int drawActually(int lodLevel) {
        if(hasIndexBuffer) {
            lodLevel = Math.min(indexBuffers.size() - 1, lodLevel);
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexBuffers.get(lodLevel));
            return indexBuffers.get(lodLevel).capacity()/3;
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
            return verticesCount;
        }
    }

    @Override
    public void bind() {
        super.bind();
        if(vertexArrayObject != null) {
            vertexArrayObject.bind();
        }
    }

    @Override
    public int getPrimitiveSizeInBytes() {
        return 4;
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        return buffer;
    }

    @Override
    public FloatBuffer getValues() {
        return buffer;
    }

    @Override
    public FloatBuffer getValues(int offset, int length) {
        FloatBuffer result = BufferUtils.createFloatBuffer(length);
        for(int i = 0; i < length; i++) {
            result.put(i, buffer.get(offset+i));
        }

        result.rewind();
        return result;
    }

    @Override
    public void putValues(FloatBuffer values) {
        buffer.put(values);
    }

    @Override
    public void putValues(DoubleBuffer values) {
        throw new NotImplementedException();
    }

    @Override
    public void putValues(int offset, FloatBuffer values) {
        if(values == buffer) { return; }
        if(values.capacity() > getSizeInBytes()) { setSizeInBytes(values.capacity());}
        bind();
        values.rewind();
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public void putValues(int offset, DoubleBuffer values) {
        throw new NotImplementedException();
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
        bind();
        setCapacityInBytes((offset + values.length)* getPrimitiveSizeInBytes());
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public void putValues(int offset, double... values) {
        throw new NotImplementedException();
    }

    @Override
    public void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        setCapacityInBytes(bufferable[0].getElementsPerObject() * getPrimitiveSizeInBytes() * bufferable.length);

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            int currentOffset = i * currentBufferable.getElementsPerObject();
            double[] currentBufferableArray = currentBufferable.get();
            for (int z = 0; z < currentBufferableArray.length; z++) {
                buffer.put(offset+currentOffset + z, (float) currentBufferableArray[z]);
            }
        }
    }

    @Override
    public void put(Bufferable... bufferable) {
        put(0, bufferable);
    }

    public void drawAgain() {
        if(!uploaded) { return; }
        drawActually(0);
    }

    public int drawDebug() {
        return drawDebug(0);
    }

    public int drawDebug(int lodLevel) {
		if(!uploaded) { return 0; }
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(1f);
        bind();
        drawActually(lodLevel);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        return verticesCount;
	}
    public int drawDebug(float lineWidth) {
        return drawDebug(lineWidth, 0);
    }
    public int drawDebug(float lineWidth, int lodLevel) {
		if(!uploaded) { return 0; }
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(lineWidth);
		bind();
        drawActually(lodLevel);
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

    private void setVertexArrayObject(VertexArrayObject vertexArrayObject) {
        this.vertexArrayObject = vertexArrayObject;
    }

    public void setIndexBufferNames(int indexBufferName) {
        this.indexBufferNames[0] = indexBufferName;
    }

    public void setHasIndexBuffer(boolean hasIndexBuffer) {
        this.hasIndexBuffer = hasIndexBuffer;
    }
}
