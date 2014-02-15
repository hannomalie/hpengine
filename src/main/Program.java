package main;

import static main.log.ConsoleLogger.getLogger;

import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.util.Util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class Program {
	private static Logger LOGGER = getLogger();
	
	private int id;
	private int vertexShaderId = 0;
	private int fragmentShaderId = 0;
	
	private EnumSet<DataChannels> channels;
	
	public Program(String vertexShaderLocation, String fragmentShaderLocation, EnumSet<DataChannels> channels) {
		this.channels = channels;
		vertexShaderId = Program.loadShader(vertexShaderLocation, GL20.GL_VERTEX_SHADER);
		fragmentShaderId = Program.loadShader(fragmentShaderLocation, GL20.GL_FRAGMENT_SHADER);
		id = GL20.glCreateProgram();
		GL20.glAttachShader(id, vertexShaderId);
		GL20.glAttachShader(id, fragmentShaderId);
		bindShaderAttributeChannels();
		
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);
		
		use();
	}
	
	public void use() {
		GL20.glUseProgram(id);
	}
	
	private void bindShaderAttributeChannels() {
		LOGGER.log(Level.INFO, "Binding shader input channels:");
		EnumSet<DataChannels> channels = EnumSet.allOf(DataChannels.class);
		for (DataChannels channel: channels) {
			GL20.glBindAttribLocation(id, channel.getLocation(), channel.getBinding());
			LOGGER.log(Level.INFO, String.format("Program(%d): Bound GL attribute location for %s with %s", id, channel.getLocation(), channel.getBinding()));
		}
	}
	
	public void delete() {
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(id);
	}

	public int getId() {
		return id;
	}
	private void setId(int id) {
		this.id = id;
	}
	public int getVertexShaderId() {
		return vertexShaderId;
	}
	public void setVertexShaderId(int vertexShaderId) {
		this.vertexShaderId = vertexShaderId;
	}
	public int getFragmentShaderId() {
		return fragmentShaderId;
	}
	public void setFragmentShaderId(int fragmentShaderId) {
		this.fragmentShaderId = fragmentShaderId;
	}

	public static int loadShader(String filename, int type) {
		String shaderSource;
		int shaderID = 0;
		
		shaderSource = Util.loadAsTextFile(filename);
		
		shaderID = GL20.glCreateShader(type);
		GL20.glShaderSource(shaderID, shaderSource);
		GL20.glCompileShader(shaderID);
		
		if (GL20.glGetShader(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not compile shader.");
			System.err.println(GL20.glGetShaderInfoLog(shaderID, 10000));
			System.exit(-1);
		}
		
		ForwardRenderer.exitOnGLError("loadShader");
		
		return shaderID;
	}
}
