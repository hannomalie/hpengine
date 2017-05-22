package de.hanno.hpengine.engine.graphics.shader;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public class UniformBlock extends Uniform {

	private int bufferId;
	// blockIndex = location
	private int blockSize;

	public String[] names = new String[]{};
	public FloatBuffer buffer;

	public UniformBlock(AbstractProgram program, String uniformName, String... names) {
		super(program, uniformName);
		setLocationIfAbsent();
		this.names = names;
		bufferId = GL15.glGenBuffers();
		blockSize = GL31.glGetActiveUniformBlocki(program.getId(), location, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, bufferId);
		GL31.glUniformBlockBinding(program.getId(), location, 0);
	}
	
	public Uniform setLocationIfAbsent() {
		if (location == -1) {
			location = GL31.glGetUniformBlockIndex(program.getId(), name);
		}
		return this;
	}
	
	public void set(FloatBuffer buffer) {
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, bufferId);
		GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
	}
	
	public void set(float[] values) {
		buffer = BufferUtils.createFloatBuffer(values.length);
		buffer.put(values);
		set(buffer);
	}
	

}
