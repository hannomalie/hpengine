package main.shader;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.DataChannels;
import main.Renderer;
import main.util.Util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.util.vector.Vector3f;

public class Program {
	private static Logger LOGGER = getLogger();
	
	private int id;
	private int vertexShaderId = 0;
	private int geometryShaderId = 0;
	private int fragmentShaderId = 0;
	
	private EnumSet<DataChannels> channels;
	
	private HashMap<String, Uniform> uniforms = new HashMap<>();

	private boolean needsTextures = true;

	public Program(String geometryShaderLocation, String vertexShaderLocation, String fragmentShaderLocation, EnumSet<DataChannels> channels) {
		this(geometryShaderLocation, vertexShaderLocation, fragmentShaderLocation, channels, true);
	}
	public Program(String geometryShaderLocation, String vertexShaderLocation, String fragmentShaderLocation, EnumSet<DataChannels> channels, boolean needsTextures) {
		this.channels = channels;
		this.needsTextures = needsTextures;
		vertexShaderId = Program.loadShader(vertexShaderLocation, GL20.GL_VERTEX_SHADER);
		fragmentShaderId = Program.loadShader(fragmentShaderLocation, GL20.GL_FRAGMENT_SHADER);
		id = GL20.glCreateProgram();
		GL20.glAttachShader(id, vertexShaderId);
		GL20.glAttachShader(id, fragmentShaderId);
		if (geometryShaderLocation != null) {
			geometryShaderId = Program.loadShader(geometryShaderLocation, GL32.GL_GEOMETRY_SHADER);
			GL20.glAttachShader(id, geometryShaderId);
		}
		bindShaderAttributeChannels();
		
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);
		
		use();
	}

	public Program(String vertexShaderLocation, String fragmentShaderLocation, EnumSet<DataChannels> channels) {
		this(null, vertexShaderLocation, fragmentShaderLocation, channels, true);
	}
	public Program(String vertexShaderLocation, String fragmentShaderLocation, EnumSet<DataChannels> channels, boolean needsTextures) {
		this(null, vertexShaderLocation, fragmentShaderLocation, channels, needsTextures);
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
		
		Renderer.exitOnGLError("loadShader");
		
		return shaderID;
	}

	public boolean needsTextures() {
		return needsTextures;
	}

	public void setUniform(String name, int value) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(value);
	}
	public void setUniform(String name, float value) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(value);
	}
	public void setUniform(String name, double value) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(value);
	}
	public void setUniformAsMatrix4(String name, FloatBuffer matrixBuffer) {
		putInMapIfAbsent(name);
		uniforms.get(name).setAsMatrix4(matrixBuffer);
	}
	public void setUniform(String name, float x, float y, float z) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(x, y, z);
	}
	public void setUniform(String name, Vector3f vec) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(vec.x, vec.y, vec.z);
	}
	
	
	
	private void putInMapIfAbsent(String name) {
		if (!uniforms.containsKey(name)) {
			uniforms.put(name, new Uniform(this, name));
		}
	}
	public int getUniformLocation(String name) {
		return GL20.glGetUniformLocation(getId(), name);
	}
	public Uniform getUniform(String key) {
		return uniforms.get(key);
	}
	public void addEmptyUniform(Uniform uniform) {
		uniforms.put(uniform.name, uniform);
	}
}