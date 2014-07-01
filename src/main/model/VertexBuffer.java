package main.model;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.logging.Logger;

import main.renderer.Renderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class VertexBuffer {
	private static Logger LOGGER = getLogger();
	
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

	private int vertexBuffer = 0;
	private int vertexArray = 0;
	private FloatBuffer buffer;
	private int verticesCount;
	private Renderer renderer;
	public EnumSet<DataChannels> channels;
	private Usage usage;
	private float[] vertices;

	private Vector4f min;
	private Vector4f max;

	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels, Usage usage) {
		this.buffer = buffer;
		this.channels = channels;
		this.usage = usage;
		if(buffer != null) {
			this.verticesCount = calculateVerticesCount(buffer, channels);
//			LOGGER.log(Level.INFO, String.format("Created VertexBuffer(%d vertices) with %d bytes %s",verticesCount, buffer.remaining() * 4, channels.toString()));	
		} else {
//			LOGGER.log(Level.INFO, String.format("Created VertexBuffer"));
		}
	}
	
	public VertexBuffer(FloatBuffer buffer, EnumSet<DataChannels> channels) {
		this( buffer, channels, Usage.DYNAMIC);
		this.verticesCount = calculateVerticesCount(buffer, channels);
		buffer.rewind();
		float[] floatArray = new float[buffer.limit()/4];
		buffer.get(floatArray);
		this.vertices = floatArray;
	}

	public VertexBuffer(float[] values, EnumSet<DataChannels> channels) {
		this( null, channels, Usage.DYNAMIC);
//		this.verticesCount = calculateVerticesCount(values, channels);
		buffer = buffer(values, channels);
//		LOGGER.log(Level.INFO, String.format("Added Buffer(%d vertices) with %d bytes %s", verticesCount, buffer.remaining() * 4, channels.toString()));
//		LOGGER.log(Level.INFO, String.format("Created VertexBuffer(%d vertices) with %d bytes %s", verticesCount, buffer.remaining() * 4, channels.toString()));
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
		// VBO
		vertexBuffer = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
		buffer.rewind();
//		LOGGER.log(Level.INFO, String.format("Buffering %d bytes to GPU", buffer.remaining() * 4));
		
		// VAO, yea
		vertexArray = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(vertexArray);
		setUpAttributes();
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, usage.getValue());
		GL30.glBindVertexArray(0);
		
		return this;
	}

	public void draw() {
//		LOGGER.log(Level.INFO, String.format("Binding VertexArray", vertexArray));
		GL30.glBindVertexArray(vertexArray);
//		LOGGER.log(Level.INFO, String.format("Drawing %d vertices => %d triangles", verticesCount, verticesCount/3));
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
	}
	public void drawAgain() {
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount);
	}
	
	public void drawDebug() {
		GL30.glBindVertexArray(vertexArray);
		GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount);
	}
	
	private void setUpAttributes() {
		
		int currentOffset = 0;
		for (DataChannels channel : channels) {
			GL20.glEnableVertexAttribArray(channel.getLocation());
			GL20.glVertexAttribPointer(channel.getLocation(),channel.getSize(), GL11.GL_FLOAT, false, bytesPerVertex(channels), currentOffset);
			
//			LOGGER.log(Level.INFO, "Enabled " + channel.getBinding() + " on " + channel.getLocation() + " (" + channel.getSize() + " elements) with current offset " + currentOffset);
			
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
