package shader;

import static log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import engine.AppContext;
import engine.model.DataChannels;
import event.GlobalDefineChangedEvent;
import org.lwjgl.util.glu.GLU;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.command.Result;
import renderer.command.Command;
import util.TypedTuple;
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

	protected Program(Renderer renderer, String vertexShaderName, String geometryShaderName, String fragmentShaderName, EnumSet<DataChannels> channels, boolean needsTextures, String fragmentDefines) {
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
		AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
			clearUniforms();
			setId(GL20.glCreateProgram());

			try {
				GL20.glAttachShader(id, loadShader(vertexShaderName, GL20.GL_VERTEX_SHADER));
			} catch (Exception e) {
				try {
					GL20.glAttachShader(id, loadShader(ProgramFactory.FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, GL20.GL_VERTEX_SHADER));
				} catch (Exception e1) {
					System.err.println("Not able to load default vertex shader, so what else could be done...");
				}
			}
			try {
				GL20.glAttachShader(id, loadShader(fragmentShaderName, GL20.GL_FRAGMENT_SHADER, fragmentDefines));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (geometryShaderName != null && geometryShaderName != "") {
				try {
					GL20.glAttachShader(id, loadShader(geometryShaderName, GL32.GL_GEOMETRY_SHADER));
					System.out.println("Attach geometryshader " + GLU.gluErrorString(GL11.glGetError()));
				} catch (Exception e) {
					System.out.println("Not able to load geometry shader, so what else could be done...");
				}
			}

			bindShaderAttributeChannels();

			GL20.glLinkProgram(id);
			printError("Link program");
			GL20.glValidateProgram(id);
			printError("Validate program");

//		use(); // CAN CAUSE INVALID OPERATION - TODO: CHECK OUT WHY
			addFileListeners();
		});
	}

	private void printError(String text) {
		System.out.println(text + " " + GLU.gluErrorString(GL11.glGetError()));
	}

	private void addFileListeners() {
		
		clearListeners();

		reloadOnFileChangeListener = new ReloadOnFileChangeListener<Program>(this) {

			@Override
			public boolean shouldReload(File changedFile) {
				String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
				if(fileName.startsWith("globals")) {
					return true;
				}

				if(fragmentShaderName != null && fragmentShaderName.startsWith(fileName) ||
					vertexShaderName != null && vertexShaderName.startsWith(fileName) ||
					geometryShaderName != null && geometryShaderName.startsWith(fileName)) {
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

		CompletableFuture<Boolean> future = OpenGLContext.getInstance().doWithOpenGLContext(() -> {
			self.unload();
			self.load();
			return true;
		});
		try {
			Boolean result = future.get(5, TimeUnit.MINUTES);
			if (result.equals(Boolean.TRUE)) {
				System.out.println("Program reloaded");
			} else {
				System.out.println("Program not reloaded");
			}
		} catch (Exception e1) {
			System.out.println("Program not reloaded");
		}
	}

	@Override
	public String getName() {
		return new StringJoiner(", ").add(fragmentShaderName).add(vertexShaderName)
				.toString();
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
			String shaderFileAsText = FileUtils.readFileToString(new File(getDirectory() + filename));
			TypedTuple<String, Integer> tuple = replaceIncludes(shaderFileAsText, newlineCount);
			shaderFileAsText = tuple.getLeft();
			newlineCount = tuple.getRight();
			shaderSource += shaderFileAsText;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		shaderID = GL20.glCreateShader(type);
		GL20.glShaderSource(shaderID, shaderSource);
		GL20.glCompileShader(shaderID);

		if (GL20.glGetShader(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not compile shader: " + filename);
//			System.err.println("Dynamic code takes " + newlineCount + " lines");
			String shaderInfoLog = GL20.glGetShaderInfoLog(shaderID, 10000);
			shaderInfoLog = replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, newlineCount);
			System.err.println(shaderInfoLog);
			throw new Exception();
		}
//		System.out.println(shaderSource);
		Renderer.exitOnGLError("loadShader");
		
		return shaderID;
	}

	private static TypedTuple<String, Integer> replaceIncludes(String shaderFileAsText, int currentNewLineCount) throws IOException {

		Pattern includePattern = Pattern.compile("//include\\((.*)\\)");
		Matcher includeMatcher = includePattern.matcher(shaderFileAsText);

		while (includeMatcher.find()) {
			String filename = includeMatcher.group(1);
			String fileToInclude = FileUtils.readFileToString(new File(getDirectory() + filename));
			currentNewLineCount += Util.countNewLines(fileToInclude);
			shaderFileAsText = shaderFileAsText.replaceAll(String.format("//include\\(%s\\)", filename),
					fileToInclude);
		}

		return new TypedTuple<>(shaderFileAsText, new Integer(currentNewLineCount));
	}

	private static String replaceLineNumbersWithDynamicLinesAdded(String shaderInfoLog, int newlineCount) {

		Pattern loCPattern = Pattern.compile("\\((\\w+)\\) :");
		Matcher loCMatcher = loCPattern.matcher(shaderInfoLog);

		while (loCMatcher.find()) {
			String oldLineNumber = loCMatcher.group(1);
			int newLineNumber = Integer.parseInt(oldLineNumber) - newlineCount;
			shaderInfoLog = shaderInfoLog.replaceAll(String.format("\\(%s\\) :", oldLineNumber), String.format("(%d) :", newLineNumber));
		}

		return shaderInfoLog;
	}

	public static int loadShader(String filename, int type) throws Exception {
		return loadShader(filename, type, "");
	}

	public boolean needsTextures() {
		return needsTextures;
	}

	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/shaders/";
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
