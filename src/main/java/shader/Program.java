package shader;

import static log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import engine.AppContext;
import engine.model.DataChannels;
import event.GlobalDefineChangedEvent;
import renderer.Renderer;
import renderer.command.Result;
import renderer.command.Command;
import util.Util;
import util.ressources.FileMonitor;
import util.ressources.ReloadOnFileChangeListener;
import util.ressources.Reloadable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import com.google.common.eventbus.Subscribe;

public class Program extends AbstractProgram implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private EnumSet<DataChannels> channels;
	private boolean needsTextures = true;

	private Map<String, Object> localDefines = new HashMap<>();
	
	private String geometryShaderName;
	private String vertexShaderName;
	private String fragmentShaderName;

	private String fragmentDefines;

	private ReloadOnFileChangeListener<Program> reloadOnFileChangeListener;

	private FileAlterationObserver observerFragmentShader;
	private Renderer renderer;

	protected Program(Renderer renderer, String geometryShaderName, String vertexShaderName, String fragmentShaderName, EnumSet<DataChannels> channels, boolean needsTextures, String fragmentDefines) {
		this.channels = channels;
		this.needsTextures = needsTextures;
		this.fragmentDefines = fragmentDefines;
		
		this.geometryShaderName = geometryShaderName;
		this.vertexShaderName = vertexShaderName;
		this.fragmentShaderName = fragmentShaderName;
		this.renderer = renderer;

		observerFragmentShader = new FileAlterationObserver(getDirectory());

		addFileListeners();
		load();
	}
	
	public void load() {
		clearUniforms();
		setId(GL20.glCreateProgram());
		
		try {
			GL20.glAttachShader(id, loadShader(vertexShaderName, GL20.GL_VERTEX_SHADER));
		} catch (Exception e) {
			try {
				GL20.glAttachShader(id, loadShader(ProgramFactory.FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, GL20.GL_VERTEX_SHADER));
			} catch (Exception e1) {
				System.err.println("Not able to load default vertex shader, so what else could be done...");
//				System.exit(-1);
			}
		}
		try {
			GL20.glAttachShader(id, loadShader(fragmentShaderName, GL20.GL_FRAGMENT_SHADER, fragmentDefines));
		} catch (Exception e) {
			try {
				GL20.glAttachShader(id, loadShader(ProgramFactory.FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE, GL20.GL_FRAGMENT_SHADER, fragmentDefines));
			} catch (Exception e1) {
				System.err.println("Not able to load default vertex shader, so what else could be done...");
//				System.exit(-1);
			}
		}
		if (geometryShaderName != null && geometryShaderName != "") {
			try {
				GL20.glAttachShader(id, loadShader(geometryShaderName, GL32.GL_GEOMETRY_SHADER));
			} catch (Exception e) {
				System.err.println("Not able to load geometry shader, so what else could be done...");
//				System.exit(-1);
			}
		}
		
		bindShaderAttributeChannels();
		
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);

//		use(); // CAN CAUSE INVALID OPERATION - TODO: CHECK OUT WHY
		addFileListeners();
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
	
	public void reload() {
		final Program self = this;
		
		SynchronousQueue<Result> queue = renderer.addCommand(new Command<Result>(){
			@Override
			public Result execute(AppContext world) {
				self.unload();
				self.load();
				return new Result();
			}
		});
		Result result = null;
		try {
			result = queue.poll(5, TimeUnit.MINUTES);
			if (!result.isSuccessful()) {
				System.out.println("Program not reloaded");
			} else {
				System.out.println("Program reloaded");
			}
		} catch (Exception e1) {
			System.out.println("Program not reloaded");
		}
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
		String shaderSource = "";
		int shaderID = 0;
		
		shaderSource = "#version 430 core \n" + mapDefinesString + "\n" + ShaderDefine.getGlobalDefinesString();	

		String findStr = "\n";
		int newlineCount = (shaderSource.split(findStr, -1).length-1);

//		System.out.println(shaderSource);
		
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
			System.err.println("Dynamic code takes " + newlineCount + " lines");
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

	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/shaders/deferred/";
	}

	public void addDefine(String name, Object define) {
		localDefines.put(name, define);
	}
	public void removeDefine(String name) {
		localDefines.remove(name);
	}

	@Override
	@Subscribe
	public void handle(GlobalDefineChangedEvent e) {
		reload();
	}

	public String getDefineString() {
		StringBuilder builder = new StringBuilder();

		for (Map.Entry<String, Object> shaderDefine : localDefines.entrySet()) {
			builder.append(getDefineTextForObject(shaderDefine));
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getDefineTextForObject(Map.Entry<String, Object> define) {
		if(define.getValue() instanceof Boolean) {
			return "const bool " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else if(define.getValue() instanceof Integer) {
			return "const int " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else if(define.getValue() instanceof Float) {
			return "const float " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else {
			Logger.getGlobal().info("Local define not supported type for " + define.getKey() + " - " + define.getValue());
			return "";
		}
	}
}
