package main.shader;

import static main.log.ConsoleLogger.getLogger;

import java.util.logging.Logger;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;


public class ComputeShaderProgram {
	private static Logger LOGGER = getLogger();
	
	private int id;
	private int computeShaderId = 0;

	public ComputeShaderProgram(String computeShaderLocation) {
		computeShaderId = Program.loadShader(computeShaderLocation, GL43.GL_COMPUTE_SHADER);
		id = GL20.glCreateProgram();
		GL20.glAttachShader(id, computeShaderId);
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);
	}

	public void use() {
		GL20.glUseProgram(id);
	}
	
	public void dispatchCompute(int num_groups_x, int num_groups_y, int num_groups_z) {
		GL43.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z);
	}

	public int getId() {
		return id;
	}

}
