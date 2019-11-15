package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL42;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

public class VertexBuffer extends PersistentMappedBuffer {

    static final Logger LOGGER = Logger.getLogger(VertexBuffer.class.getName());

    GpuContext gpuContext;

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

    public VertexBuffer(GpuContext gpuContext, float[] values, EnumSet<DataChannels> channels) {
        super(gpuContext, 64, GL15.GL_ARRAY_BUFFER);
        setInternals(gpuContext, values, channels);
	}
	public VertexBuffer(GpuContext gpuContext, FloatBuffer buffer, EnumSet<DataChannels> channels) {
        super(gpuContext, 64, GL15.GL_ARRAY_BUFFER);
        setInternals(gpuContext, buffer, channels);
	}

    public VertexBuffer(GpuContext gpuContext, FloatBuffer verticesFloatBuffer, EnumSet<DataChannels> channels, IndexBuffer ... indicesBuffer) {
        this(gpuContext, verticesFloatBuffer, channels);
    }

    private void setInternals(GpuContext gpuContext, FloatBuffer buffer, EnumSet<DataChannels> channels) {
        float[] values = new float[buffer.capacity()];
        buffer.get(values);
        setInternals(gpuContext, values, channels);
    }

    private void setInternals(GpuContext gpuContext, float[] values, EnumSet<DataChannels> channels) {
        this.gpuContext = gpuContext;
        this.channels = channels;
        gpuContext.execute("VertexBuffer.setInternals", () -> {
            bind();
            setVertexArrayObject(VertexArrayObject.getForChannels(gpuContext, channels));
        });
        ensureCapacityInBytes(values.length * Float.BYTES);
        this.verticesCount = calculateVerticesCount(getBuffer(), channels);
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
					getBuffer().putFloat(vertices[(i*totalElementsPerVertex) + currentOffset + a]);
				}
				currentOffset += channel.getSize();
			}
		}

		getBuffer().rewind();
		return getBuffer().asFloatBuffer();
	}

	public int getVerticesCount() {
//		verticesCount = calculateVerticesCount(channels);
        triangleCount = verticesCount / 3;
		return verticesCount;
	}

	private int calculateVerticesCount(EnumSet<DataChannels> channels) {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
		validate(totalElementsPerVertex);

		int verticesCount = getBuffer().capacity() / Float.BYTES / totalElementsPerVertex;
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
		int modulo = getBuffer().capacity() % totalElementsPerVertex;
		if (modulo != 0) {
			throw new RuntimeException(String.format("Can't buffer those vertices!\n" +
					"vertices count: %d,\n" +
					"Attribute values per vertex: %d\n" +
					"=> Modulo is %d", getBuffer().capacity(), totalElementsPerVertex, modulo));
		}
	}

    public CompletableFuture<VertexBuffer> upload() {
        getBuffer().rewind();
        CompletableFuture<VertexBuffer> future = new CompletableFuture<>();
        gpuContext.execute("VertexBuffer.upload", () -> {
            bind();
            setVertexArrayObject(VertexArrayObject.getForChannels(gpuContext, channels));
            future.complete(VertexBuffer.this);
        });
        return future;
    }

    public void delete() {
		GL15.glDeleteBuffers(getId());
        vertexArrayObject.delete();
		setBuffer(null);
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
            indexBuffer.bind();
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 4*indexOffset, instanceCount, baseVertexIndex, 0);

        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
        }

        return indexCount;
    }

    public int drawInstancedBaseVertex(IndexBuffer indexBuffer, DrawElementsIndirectCommand command) {
        return drawInstancedBaseVertex(indexBuffer, command.getCount(), command.getPrimCount(), command.getFirstIndex(), command.getBaseVertex());
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

    public int drawLinesInstancedBaseVertex(IndexBuffer indexBuffer, DrawElementsIndirectCommand command) {
        return drawLinesInstancedBaseVertex(indexBuffer, command.getCount(), command.getPrimCount(), command.getFirstIndex(), command.getBaseVertex());
    }

    public static void multiDrawElementsIndirectCount(VertexBuffer vertexBuffer,
                                                      IndexBuffer indexBuffer,
                                                      PersistentMappedStructBuffer<DrawElementsIndirectCommand> commandBuffer,
                                                      AtomicCounterBuffer drawCountBuffer,
                                                      int maxDrawCount) {
        drawCountBuffer.bindAsParameterBuffer();
        vertexBuffer.bind();
        indexBuffer.bind();
        commandBuffer.bind();
        ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, 0, maxDrawCount, 0);
        drawCountBuffer.unbind();
        indexBuffer.unbind();
    }

    public static void multiDrawElementsIndirectCount(VertexIndexBuffer vertexIndexBuffer,
                                                      PersistentMappedStructBuffer<DrawElementsIndirectCommand> commandBuffer,
                                                      AtomicCounterBuffer drawCountBuffer,
                                                      int maxDrawCount) {
        multiDrawElementsIndirectCount(vertexIndexBuffer.getVertexBuffer(),
                vertexIndexBuffer.getIndexBuffer(), commandBuffer, drawCountBuffer, maxDrawCount);
    }

    public static void multiDrawElementsIndirect(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, PersistentMappedStructBuffer<DrawElementsIndirectCommand> commandBuffer, int primitiveCount) {
        vertexBuffer.bind();
        indexBuffer.bind();
        commandBuffer.bind();
        glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0);
        indexBuffer.unbind();
    }

    public static void drawLinesInstancedIndirectBaseVertex(VertexIndexBuffer vertexIndexBuffer, PersistentMappedStructBuffer<DrawElementsIndirectCommand> commandBuffer, int primitiveCount) {
        drawLinesInstancedIndirectBaseVertex(vertexIndexBuffer.getVertexBuffer(), vertexIndexBuffer.getIndexBuffer(), commandBuffer, primitiveCount);
    }

    public static void drawLinesInstancedIndirectBaseVertex(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, PersistentMappedStructBuffer<DrawElementsIndirectCommand> commandBuffer, int primitiveCount) {
        vertexBuffer.bind();
        indexBuffer.bind();
        commandBuffer.bind();
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glLineWidth(1f);
        glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0);
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
    public void putValues(int floatOffset, float... values) {
//        bind();
        ensureCapacityInBytes((floatOffset + values.length) * Float.BYTES);
        FloatBuffer floatBuffer = getBuffer().asFloatBuffer();
        floatBuffer.position(floatOffset);
        floatBuffer.put(values);
        getBuffer().rewind();

        int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);
        verticesCount = (floatOffset+values.length)/totalElementsPerVertex;
        triangleCount = verticesCount/3;
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
    public int drawDebugLines(float lineWidth) {
        if(!uploaded) { return 0; }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glLineWidth(lineWidth);
        bind();
        GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        return verticesCount;
    }
    public int drawDebugLines() {
        return drawDebugLines(2f);
    }

    public float[] getVertexData() {
		int totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels);

		float[] result = new float[totalElementsPerVertex * verticesCount];

		getBuffer().rewind();
		getBuffer().asFloatBuffer().get(result);
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
        FloatBuffer floatBuffer = getBuffer().asFloatBuffer();
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
