package engine.model;

import engine.AppContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLThread;
import renderer.Renderer;
import renderer.command.Command;
import renderer.command.Result;

import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.concurrent.SynchronousQueue;

import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL32.glFenceSync;
import static org.lwjgl.opengl.GL32.glWaitSync;

public class VertexBuffer {

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

	private volatile int vertexBuffer = 0;
	private volatile int vertexArray = 0;
	private FloatBuffer buffer;
	private int verticesCount;
	public EnumSet<DataChannels> channels;
	private Usage usage;
	private float[] vertices;

	private Vector4f min;
	private Vector4f max;

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
		buffer.rewind();
		float[] floatArray = new float[buffer.limit()/4];
		buffer.get(floatArray);
		this.vertices = floatArray;
	}

	private void setInternals(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
		this.buffer = buffer;
		this.channels = channels;
		this.usage = usage;
		this.verticesCount = calculateVerticesCount(buffer, channels);
	}


	private FloatBuffer buffer(float[] vertices, EnumSet<DataChannels> channels) {
		
		int totalElementsPerVertex = totalElementsPerVertex(channels);
		int totalBytesPerVertex = totalElementsPerVertex * 4;
		this.verticesCount = calculateVerticesCount(vertices, channels);
		this.vertices = vertices;

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
		verticesCount = calculateVerticesCount(vertices, channels);
		return verticesCount;
	}

	private int calculateVerticesCount(float[] vertices, EnumSet<DataChannels> channels) {
		int totalElementsPerVertex = totalElementsPerVertex(channels);
		validate(vertices, totalElementsPerVertex);
		
		int verticesCount = vertices.length / totalElementsPerVertex;
		return verticesCount;
	}
	
	public int calculateVerticesCount(FloatBuffer floatBuffer, EnumSet<DataChannels> channels) {
		floatBuffer.rewind();
		float[] floatArray = new float[floatBuffer.limit()];
		floatBuffer.get(floatArray);
		return calculateVerticesCount(floatArray, channels);
	}

	public static int totalElementsPerVertex(EnumSet<DataChannels> channels) {
		int count = 0;
		for (DataChannels channel : channels) {
			count += channel.getSize();
		}
		return count;
	}
	
	public int totalElementsPerVertex() {
		return VertexBuffer.totalElementsPerVertex(this.channels);
		
	}

	// TODO: Mach irgendwas....
	private void validate(float[] vertices, int totalElementsPerVertex) {
		int modulo = vertices.length % totalElementsPerVertex;
		if (modulo != 0) {
			throw new RuntimeException(String.format("Can't buffer those vertices!\n" +
					"vertices count: %d,\n" +
					"Attribute values per vertex: %d\n" +
					"=> Modulo is %d", vertices.length, totalElementsPerVertex, modulo));
		}
	}
	
	public VertexBuffer upload() {
//        SynchronousQueue<Result<Object>> queue = AppContext.getInstance().getRenderer().addCommand(new Command<Result<Object>>() {
//            @Override
//            public Result<Object> execute(AppContext appContext) {
//                vertexBuffer = GL15.glGenBuffers();
//                vertexArray = GL30.glGenVertexArrays();
//
//                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
//                buffer.rewind();
//                GL30.glBindVertexArray(vertexArray);
//                setUpAttributes();
//                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, usage.getValue());
//                GL30.glBindVertexArray(0);
//                return new Result<Object>(true);
//            }
//        });
//        queue.poll();

		AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
			vertexBuffer = GL15.glGenBuffers();
			vertexArray = GL30.glGenVertexArrays();

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
			buffer.rewind();
			GL30.glBindVertexArray(vertexArray);
			setUpAttributes();
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, usage.getValue());
			GL30.glBindVertexArray(0);
		});

		return this;
	}
	
	public void delete() {
		GL15.glDeleteBuffers(vertexBuffer);
		GL30.glDeleteVertexArrays(vertexArray);
		buffer = null;
	}

	public void draw() {
		GL30.glBindVertexArray(vertexArray);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
	}
	public void drawAgain() {
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
	}
	
	public void drawDebug() {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glLineWidth(1f);
		GL30.glBindVertexArray(vertexArray);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
	}

	public void drawInstanced(int instanceCount) {
		GL30.glBindVertexArray(vertexArray);
		GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount);
	}
	
	private void setUpAttributes() {
		
		int currentOffset = 0;
		for (DataChannels channel : channels) {
			GL20.glEnableVertexAttribArray(channel.getLocation());
			GL20.glVertexAttribPointer(channel.getLocation(),channel.getSize(), GL11.GL_FLOAT, false, bytesPerVertex(channels), currentOffset);
			
			currentOffset += channel.getSize() * 4;
		}
	}
	
	public static int bytesPerVertex(EnumSet<DataChannels> channels) {
		int sum = 0;
		for (DataChannels channel : channels) {
			sum += channel.getSize();
		}
		return sum * 4;
	}
	
	public float[] getVertexData() {
		int totalElementsPerVertex = totalElementsPerVertex(channels);
	
		float[] result = new float[totalElementsPerVertex * verticesCount];
		
		buffer.rewind();
		buffer.get(result);
		return result;
	}
	
	public Vector4f[] getMinMax() {
		
		if (min == null || max == null) {
			float[] positions = getValues(DataChannels.POSITION3);
			min = new Vector4f(positions[0],positions[1],positions[2],0);
			max = new Vector4f(positions[0],positions[1],positions[2],0);
			
			for (int i = 0; i < positions.length; i+=3) {
				
				Vector3f position = new Vector3f(positions[i],positions[i+1],positions[i+2]);

				min.x = position.x < min.x ? position.x : min.x;
				min.y = position.y < min.y ? position.y : min.y;
				min.z = position.z < min.z ? position.z : min.z;
				min.w = 1;
				
				max.x = position.x > max.x ? position.x : max.x;
				max.y = position.y > max.y ? position.y : max.y;
				max.z = position.z > max.z ? position.z : max.z;
				max.w = 1;
			}
			
		}
		
		return new Vector4f[] {min, max};
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
		for (int i = stride; i < vertices.length; i += stride + elementsPerChannel + elementCountAfterPositions) {
			for (int x = 0; x < forChannel.getSize(); x++) {
				
				result[resultIndex] = vertices[i+x];
				resultIndex++;
			}
		}
		
		return result;
		
	}
}
