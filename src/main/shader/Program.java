package main.shader;

import static main.log.ConsoleLogger.getLogger;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import main.World;
import main.model.DataChannels;
import main.model.Entity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.util.Util;
import main.util.ressources.FileMonitor;
import main.util.ressources.OnFileChangeListener;
import main.util.ressources.ReloadOnFileChangeListener;
import main.util.ressources.Reloadable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.util.vector.Vector3f;

public class Program implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private int id;
	
	private EnumSet<DataChannels> channels;
	
	private HashMap<String, Uniform> uniforms;

	private boolean needsTextures = true;
	
	private String geometryShaderName;
	private String vertexShaderName;
	private String fragmentShaderName;

	private String fragmentDefines;

	private ReloadOnFileChangeListener<Program> reloadOnFileChangeListener;

	private FileAlterationObserver observerFragmentShader;

	protected Program(String geometryShaderName, String vertexShaderName, String fragmentShaderName, EnumSet<DataChannels> channels, boolean needsTextures, String fragmentDefines) {
		this.channels = channels;
		this.needsTextures = needsTextures;
		this.fragmentDefines = fragmentDefines;
		
		this.geometryShaderName = geometryShaderName;
		this.vertexShaderName = vertexShaderName;
		this.fragmentShaderName = fragmentShaderName;

		observerFragmentShader = new FileAlterationObserver(getDirectory());
		
		load();
	}
	
	public void load() {
		id = GL20.glCreateProgram();
		
		try {
			GL20.glAttachShader(id, loadShader(vertexShaderName, GL20.GL_VERTEX_SHADER));
		} catch (Exception e) {
			try {
				GL20.glAttachShader(id, loadShader(ProgramFactory.FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, GL20.GL_VERTEX_SHADER));
			} catch (Exception e1) {
				System.err.println("Not able to load default vertex shader, so what else could be done...");
				System.exit(-1);
			}
		}
		try {
			GL20.glAttachShader(id, loadShader(fragmentShaderName, GL20.GL_FRAGMENT_SHADER, fragmentDefines));
		} catch (Exception e) {
			try {
				GL20.glAttachShader(id, loadShader(ProgramFactory.FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE, GL20.GL_FRAGMENT_SHADER, fragmentDefines));
			} catch (Exception e1) {
				System.err.println("Not able to load default vertex shader, so what else could be done...");
				System.exit(-1);
			}
		}
		if (geometryShaderName != null && geometryShaderName != "") {
			try {
				GL20.glAttachShader(id, loadShader(geometryShaderName, GL32.GL_GEOMETRY_SHADER));
			} catch (Exception e) {
				System.err.println("Not able to load geometry shader, so what else could be done...");
				System.exit(-1);
			}
		}
		
		bindShaderAttributeChannels();
		
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);

		use();
		addFileListeners();
		uniforms = new HashMap<>();
	}
	
	private void addFileListeners() {
		
		clearListeners();

		reloadOnFileChangeListener = new ReloadOnFileChangeListener<Program>(this) {

			@Override
			public boolean shouldReload(File changedFile) {
				String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
				if(fragmentShaderName != null && fragmentShaderName.startsWith(fileName) ||
				   vertexShaderName != null && vertexShaderName.startsWith(fileName)) {
					return true;
				}
				return false;
			}
		};
		observerFragmentShader.addListener(reloadOnFileChangeListener);
		FileMonitor.getInstance().add(observerFragmentShader);
	}

	private void clearListeners() {
		if(observerFragmentShader != null) {
			observerFragmentShader.removeListener(reloadOnFileChangeListener);
		}
	}

	public void unload() {
		GL20.glDeleteProgram(id);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Program) || other == null) {
			return false;
		}
		
		Program otherProgram = (Program) other;
		
		if (this.channels == otherProgram.channels &&
			this.needsTextures == otherProgram.needsTextures &&
				((this.geometryShaderName == null && otherProgram.geometryShaderName == null) ||
				(this.geometryShaderName == "" && otherProgram.geometryShaderName == "") ||
				(this.geometryShaderName.equals(otherProgram.geometryShaderName))) &&
			this.vertexShaderName.equals(otherProgram.vertexShaderName) &&
			this.fragmentShaderName.equals(otherProgram.fragmentShaderName)) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (channels != null? channels.hashCode() : 0);
		hash += (geometryShaderName != null? geometryShaderName.hashCode() : 0);
		hash += (vertexShaderName != null? vertexShaderName.hashCode() : 0);
		hash += (fragmentShaderName != null? fragmentShaderName.hashCode() : 0);
		return hash;
	};
	
	public void use() {
		GL20.glUseProgram(id);
	}
	
	private void bindShaderAttributeChannels() {
//		LOGGER.log(Level.INFO, "Binding shader input channels:");
		EnumSet<DataChannels> channels = EnumSet.allOf(DataChannels.class);
		for (DataChannels channel: channels) {
			GL20.glBindAttribLocation(id, channel.getLocation(), channel.getBinding());
//			LOGGER.log(Level.INFO, String.format("Program(%d): Bound GL attribute location for %s with %s", id, channel.getLocation(), channel.getBinding()));
		}
	}
	
	public void delete() {
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(id);
	}

	public int getId() {
		return id;
	}
	
	public static int loadShader(String filename, int type, String mapDefinesString) throws Exception {
		String shaderSource;
		int shaderID = 0;
		
		shaderSource = mapDefinesString;
		if (shaderSource == null) {
			shaderSource = "";
		}
		try {
			shaderSource += FileUtils.readFileToString(new File(getDirectory() + filename));//Util.loadAsTextFile(filename);
		} catch (IOException e) {
			shaderSource += Util.loadAsTextFile(filename);
		}
		
		shaderID = GL20.glCreateShader(type);
		GL20.glShaderSource(shaderID, shaderSource);
		GL20.glCompileShader(shaderID);
		
		if (GL20.glGetShader(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not compile shader: " + filename);
			System.err.println(GL20.glGetShaderInfoLog(shaderID, 10000));
//			System.exit(-1);
			throw new Exception();
		}
		
		Renderer.exitOnGLError("loadShader");
		
		return shaderID;
	}
	
	public static int loadShader(String filename, int type) throws Exception {
		return loadShader(filename, type, "");
	}

	public boolean needsTextures() {
		return needsTextures;
	}

	public void setUniform(String name, int value) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(value);
	}
	public void setUniform(String name, boolean value) {
		int valueAsInd = value == true ? 1 : 0;
		putInMapIfAbsent(name);
		uniforms.get(name).set(valueAsInd);
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
	
	public void setUniformAsBlock(String name, float[] fs) {
		putBlockInMapIfAbsent(name);
		try {
			((UniformBlock) uniforms.get(name)).set(fs);
		} catch (ClassCastException e) {
			System.err.println("You can't set a non block uniform as block!");
			e.printStackTrace();
		}
	}

	private void putInMapIfAbsent(String name) {
		if (!uniforms.containsKey(name)) {
			uniforms.put(name, new Uniform(this, name));
		}
	}
	private void putBlockInMapIfAbsent(String name) {
		if (!uniforms.containsKey(name)) {
			uniforms.put(name, new UniformBlock(this, name));
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

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/shaders/deferred/";
	}
}
