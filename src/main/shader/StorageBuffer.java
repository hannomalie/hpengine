package main.shader;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

/**
 * @author Hanno
 *
 */
/**
 * @author Hanno
 *
 */
public class StorageBuffer {
	
	protected int id = -1;

	protected ByteBuffer buffer;
	private int size = -1;

	private FloatBuffer tempBuffer;

	public StorageBuffer(int size) {
		this(BufferUtils.createFloatBuffer(size));
	}
	
	public StorageBuffer(FloatBuffer data) {
		id = GL15.glGenBuffers();
		bind();
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_COPY);
		setSize(GL15.glGetBufferParameter(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
		unbind();
		getValues();
	}
	
	public void bind() {
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
	}
	public void unbind() {
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
	}

	
	/**
	 * @return The FloatBuffer with all the values of this buffer object
	 */
	public FloatBuffer getValues() {
		bind();
		buffer = GL15.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_READ_ONLY, null);
		FloatBuffer result = buffer.asFloatBuffer(); // TODO: As read-only?
		GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
		unbind();
		return result;
	}
	
	/**
	 * @param offset is the index of the first included float value of the buffer
	 * @param length is the count of floats that are queried
	 * @return The FloatBuffer that contains the queries values
	 */
	public FloatBuffer getValues(int offset, int length) {
		bind();
		System.out.println("Offset " + offset*4);
		System.out.println("Length " + length*4);
		System.out.println(GL15.glGetBufferParameter(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
		FloatBuffer result = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, offset*4, length*4/*bytes!*/, GL30.GL_MAP_READ_BIT , null).asFloatBuffer();
		GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
		unbind();
		return result;
	}

	public void putValues(FloatBuffer values) {
		putValues(0, values);
	}
	
	/**
	 * @param offset is the index of the first float value that will be overridden
	 * @param values is the buffer with values that should be uploaded
	 * @throws IndexOutOfBoundsException
	 */
	public void putValues(int offset, FloatBuffer values) {
		bind();
		if(offset*4 + values.capacity()*4 > size) {
			throw new IndexOutOfBoundsException(String.format("Can't put values into shader storage buffer %d (size: %d, offset %d, length %d)", id, size, offset*4, values.capacity()*4));
		}
		GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset*4, values);
		unbind();
	}
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	
	public void putValues(float ... values) {
		putValues(0, values);
	}

	public void putValues(int offset, float ... values) {
		tempBuffer = BufferUtils.createFloatBuffer(values.length);
		for (int i = 0; i < values.length; i++) {
			tempBuffer.put(offset+i, values[i]);
		}
		putValues(tempBuffer);
	}
}